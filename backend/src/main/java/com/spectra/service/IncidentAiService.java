package com.spectra.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;
import com.spectra.dto.IncidentAiDiagnosticsResponse;
import com.spectra.entity.IncidentAiAnalysis;
import com.spectra.entity.User;
import com.spectra.exception.BadRequestException;
import com.spectra.repository.AlertEventRepository;
import com.spectra.repository.AnomalyEventRepository;
import com.spectra.repository.IncidentAiAnalysisRepository;
import com.spectra.repository.IncidentNoteRepository;
import com.spectra.repository.IncidentRepository;
import com.spectra.repository.ServiceMetricRepository;
import com.spectra.repository.UserRepository;
import com.spectra.service.IncidentAiTransactionService.IncidentContext;
import com.spectra.service.IncidentAiTransactionService.ReservationResult;

/**
 * Service managing backend-orchestrated, persistent AI diagnostics for operational incidents.
 *
 * Implements decoupled transaction boundaries:
 * 1. Execution reservation and version assignment are committed in a short database transaction.
 * 2. Outbound Python RCA and RAG network calls execute with ZERO database row locks and ZERO
 *    database connections held.
 * 3. Final diagnosis (COMPLETED or FAILED) is persisted in a separate, isolated transaction.
 */
@Service
public class IncidentAiService {

    private static final Logger log = LoggerFactory.getLogger(IncidentAiService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final IncidentAiTransactionService transactionService;
    private final AiServiceClient aiServiceClient;
    private final UserRepository userRepository;

    @Autowired
    public IncidentAiService(
            IncidentAiTransactionService transactionService,
            AiServiceClient aiServiceClient,
            UserRepository userRepository) {
        this.transactionService = transactionService;
        this.aiServiceClient = aiServiceClient;
        this.userRepository = userRepository;
    }

    /**
     * Backward-compatible constructor for standalone unit tests.
     */
    public IncidentAiService(
            IncidentRepository incidentRepository,
            IncidentAiAnalysisRepository analysisRepository,
            ServiceMetricRepository metricRepository,
            AlertEventRepository alertEventRepository,
            AnomalyEventRepository anomalyEventRepository,
            IncidentNoteRepository noteRepository,
            UserRepository userRepository,
            AiServiceClient aiServiceClient) {
        this(
                new IncidentAiTransactionService(
                        incidentRepository,
                        analysisRepository,
                        metricRepository,
                        alertEventRepository,
                        anomalyEventRepository,
                        noteRepository
                ),
                aiServiceClient,
                userRepository
        );
    }

    private User getCurrentUser() {
        if (userRepository == null) return null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user could not be resolved: " + auth.getName()));
    }

    /**
     * Retrieves the composite diagnostics summary exposing both latest execution and last successful result.
     */
    public IncidentAiDiagnosticsResponse getDiagnosticsSummary(Long incidentId) {
        if (incidentId == null) {
            throw new BadRequestException("Incident ID cannot be null");
        }
        User currentUser = getCurrentUser();
        return transactionService.getDiagnosticsSummary(incidentId, currentUser);
    }

    /**
     * Backward-compatible entity query for callers and tests expecting IncidentAiAnalysis.
     */
    public IncidentAiAnalysis getLatestAnalysis(Long incidentId) {
        if (incidentId == null) {
            throw new BadRequestException("Incident ID cannot be null");
        }
        User currentUser = getCurrentUser();
        return transactionService.getLatestAnalysisEntity(incidentId, currentUser);
    }

    /**
     * Orchestrates incident AI diagnostics across decoupled transaction boundaries:
     * - Phase 1: Short DB transaction reserves version, records PROCESSING, and collates context.
     * - Phase 2: Outbound network calls to Python RCA/RAG execute WITHOUT holding database locks.
     * - Phase 3: Short DB transaction finalizes the record as COMPLETED or FAILED.
     */
    public IncidentAiAnalysis requestOrRunDiagnostics(Long incidentId, boolean forceReanalyze) {
        if (incidentId == null) {
            throw new BadRequestException("Incident ID cannot be null");
        }
        User currentUser = getCurrentUser();

        // 1. Acquire short database transaction for reservation and lock
        ReservationResult reservation = transactionService.reserveExecution(incidentId, currentUser, forceReanalyze);

        if (!reservation.proceedWithAi()) {
            return reservation.analysis();
        }

        Long analysisId = reservation.analysis().getId();
        IncidentContext context = reservation.context();

        // 2. Outbound network calls execute with ZERO database locks held
        Map<String, Object> rcaResult;
        try {
            rcaResult = aiServiceClient.analyzeRootCause(
                    context.serviceId(),
                    context.serviceName(),
                    context.title(),
                    context.description(),
                    context.metricsPayload(),
                    context.alertsPayload(),
                    context.logs()
            );
        } catch (Exception ex) {
            log.error("AI Service RCA evaluation failed unexpectedly: {}", ex.getMessage());
            return transactionService.recordFailure(analysisId, "AI microservice invocation failed: " + ex.getMessage(), null, null);
        }

        // 3. Validate RCA response presence
        if (rcaResult == null || rcaResult.isEmpty()) {
            log.warn("AI Service returned null or empty RCA response for incident ID {}", incidentId);
            return transactionService.recordFailure(analysisId, "AI microservice returned an empty or null RCA response", null, null);
        }

        // 4. Detect offline / unavailable fallback condition
        boolean isFallback = rcaResult.containsKey("primary_evidence")
                && String.valueOf(rcaResult.get("primary_evidence")).contains("AI Service offline");

        if (isFallback) {
            log.warn("AI Service offline fallback detected for incident ID {}", incidentId);
            String rawCause = (String) rcaResult.get("primary_root_cause");
            String rawCategory = (String) rcaResult.get("failure_category");
            return transactionService.recordFailure(analysisId, "AI microservice is unavailable or circuit breaker is open", rawCause, rawCategory);
        }

        // 5. Validate required RCA fields
        Object rawCause = rcaResult.get("primary_root_cause");
        if (rawCause == null || !(rawCause instanceof String) || ((String) rawCause).trim().isEmpty()) {
            log.warn("AI Service RCA response missing or empty 'primary_root_cause' for incident ID {}", incidentId);
            return transactionService.recordFailure(analysisId, "AI microservice RCA response missing or empty 'primary_root_cause'", null, null);
        }
        String primaryCause = ((String) rawCause).trim();

        Object rawRanked = rcaResult.get("ranked_causes");
        if (rawRanked != null && !(rawRanked instanceof List<?>)) {
            log.warn("AI Service RCA response 'ranked_causes' is not a list for incident ID {}", incidentId);
            return transactionService.recordFailure(analysisId, "AI microservice RCA response 'ranked_causes' is not a list", null, null);
        }

        Object rawFactors = rcaResult.get("contributing_factors");
        if (rawFactors != null && !(rawFactors instanceof List<?>)) {
            log.warn("AI Service RCA response 'contributing_factors' is not a list for incident ID {}", incidentId);
            return transactionService.recordFailure(analysisId, "AI microservice RCA response 'contributing_factors' is not a list", null, null);
        }

        Object rawCategory = rcaResult.get("failure_category");
        String failureCategory = rawCategory instanceof String s && !s.isBlank() ? s.trim() : null;

        Double confidence = null;
        Object rawConfidence = rcaResult.get("confidence_score");
        if (rawConfidence instanceof Number num) {
            confidence = num.doubleValue();
        } else if (rawConfidence != null) {
            try {
                confidence = Double.valueOf(rawConfidence.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        String evidence = rcaResult.get("primary_evidence") != null ? rcaResult.get("primary_evidence").toString() : null;

        // 6. Query matching runbooks through RAG endpoint and validate RAG response
        Map<String, Object> runbooksResult;
        try {
            String queryTerm = !primaryCause.isBlank() ? primaryCause : context.title();
            runbooksResult = aiServiceClient.queryRunbooks(queryTerm, 3, null);
        } catch (Exception ex) {
            log.error("AI Service RAG runbooks query failed: {}", ex.getMessage());
            return transactionService.recordFailure(analysisId, "Runbook retrieval failed: " + ex.getMessage(), null, null);
        }

        if (runbooksResult == null || runbooksResult.isEmpty()) {
            log.warn("AI Service returned null or empty runbooks response for incident ID {}", incidentId);
            return transactionService.recordFailure(analysisId, "Runbook retrieval returned null or empty response", null, null);
        }

        if (runbooksResult.containsKey("error") || runbooksResult.containsKey("detail")) {
            String err = runbooksResult.containsKey("error")
                    ? String.valueOf(runbooksResult.get("error"))
                    : String.valueOf(runbooksResult.get("detail"));
            log.warn("AI Service runbooks response indicated error: {}", err);
            return transactionService.recordFailure(analysisId, "Runbook retrieval error: " + err, null, null);
        }

        Object rawMatches = runbooksResult.get("matches");
        if (rawMatches == null || !(rawMatches instanceof List<?>)) {
            log.warn("AI Service runbooks response missing or malformed 'matches' list for incident ID {}", incidentId);
            return transactionService.recordFailure(analysisId, "Runbook retrieval response missing or malformed 'matches' list", null, null);
        }
        List<?> matchesList = (List<?>) rawMatches;

        // 7. Persist completed analysis in final short transaction
        try {
            String rankedCausesJson = MAPPER.writeValueAsString(rcaResult.getOrDefault("ranked_causes", Collections.emptyList()));
            String contributingFactorsJson = MAPPER.writeValueAsString(rcaResult.getOrDefault("contributing_factors", Collections.emptyList()));
            String recommendedRunbooksJson = MAPPER.writeValueAsString(matchesList);

            return transactionService.recordSuccess(
                    analysisId,
                    primaryCause,
                    failureCategory,
                    confidence,
                    evidence,
                    rankedCausesJson,
                    contributingFactorsJson,
                    recommendedRunbooksJson
            );
        } catch (Exception ex) {
            log.error("Failed to serialize or finalize AI diagnostics: {}", ex.getMessage());
            return transactionService.recordFailure(analysisId, "Analysis data serialization error: " + ex.getMessage(), null, null);
        }
    }
}
