package com.spectra.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.dto.IncidentAiDiagnosticsResponse;
import com.spectra.entity.AlertEvent;
import com.spectra.entity.AnomalyEvent;
import com.spectra.entity.Incident;
import com.spectra.entity.IncidentAiAnalysis;
import com.spectra.entity.IncidentNote;
import com.spectra.entity.Role;
import com.spectra.entity.ServiceMetric;
import com.spectra.entity.User;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.AlertEventRepository;
import com.spectra.repository.AnomalyEventRepository;
import com.spectra.repository.IncidentAiAnalysisRepository;
import com.spectra.repository.IncidentNoteRepository;
import com.spectra.repository.IncidentRepository;
import com.spectra.repository.ServiceMetricRepository;

/**
 * Dedicated transactional collaborator managing short database transactions
 * for incident AI diagnostics.
 *
 * Ensures pessimistic locks and HikariCP database connections are released
 * immediately after execution reservation, prior to any external network I/O.
 */
@Service
public class IncidentAiTransactionService {

    private static final Logger log = LoggerFactory.getLogger(IncidentAiTransactionService.class);

    private final IncidentRepository incidentRepository;
    private final IncidentAiAnalysisRepository analysisRepository;
    private final ServiceMetricRepository metricRepository;
    private final AlertEventRepository alertEventRepository;
    private final AnomalyEventRepository anomalyEventRepository;
    private final IncidentNoteRepository noteRepository;

    public IncidentAiTransactionService(
            IncidentRepository incidentRepository,
            IncidentAiAnalysisRepository analysisRepository,
            ServiceMetricRepository metricRepository,
            AlertEventRepository alertEventRepository,
            AnomalyEventRepository anomalyEventRepository,
            IncidentNoteRepository noteRepository) {
        this.incidentRepository = incidentRepository;
        this.analysisRepository = analysisRepository;
        this.metricRepository = metricRepository;
        this.alertEventRepository = alertEventRepository;
        this.anomalyEventRepository = anomalyEventRepository;
        this.noteRepository = noteRepository;
    }

    public record IncidentContext(
            Long serviceId,
            String serviceName,
            String title,
            String description,
            List<Map<String, Object>> metricsPayload,
            List<Map<String, Object>> alertsPayload,
            List<Map<String, Object>> anomaliesPayload,
            List<String> logs
    ) {}

    public record ReservationResult(
            IncidentAiAnalysis analysis,
            boolean proceedWithAi,
            boolean alreadyActive,
            boolean alreadyCompleted,
            IncidentContext context
    ) {
        public static ReservationResult alreadyActive(IncidentAiAnalysis active) {
            return new ReservationResult(active, false, true, false, null);
        }

        public static ReservationResult alreadyCompleted(IncidentAiAnalysis completed) {
            return new ReservationResult(completed, false, false, true, null);
        }

        public static ReservationResult proceed(IncidentAiAnalysis reserved, IncidentContext context) {
            return new ReservationResult(reserved, true, false, false, context);
        }
    }

    private boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    public Incident resolveOwnedIncident(Long incidentId, User currentUser) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
        if (isAuthenticated && currentUser == null) {
            throw new AccessDeniedException("Authenticated user could not be resolved");
        }

        if (currentUser != null) {
            if (isAdmin(currentUser)) {
                return incidentRepository.findById(incidentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + incidentId));
            }
            if (currentUser.getEnterprise() != null) {
                return incidentRepository.findByIdAndServiceEnterpriseId(incidentId, currentUser.getEnterprise().getId())
                        .or(() -> incidentRepository.findByIdAndServiceUserId(incidentId, currentUser.getId())
                                .filter(i -> i.getService() != null && i.getService().getEnterprise() == null))
                        .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + incidentId));
            }
            return incidentRepository.findByIdAndServiceUserId(incidentId, currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + incidentId));
        }

        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + incidentId));
    }

    /**
     * Short database transaction:
     * 1. Acquires pessimistic row lock on Incident.
     * 2. Checks for in-flight active jobs (PENDING, PROCESSING).
     * 3. Checks for cached completed analysis if !forceReanalyze.
     * 4. Computes next version and persists reservation with status PROCESSING.
     * 5. Collates multi-signal context.
     * When this method exits, the transaction commits and the row lock is RELEASED.
     */
    @Transactional
    public ReservationResult reserveExecution(Long incidentId, User currentUser, boolean forceReanalyze) {
        if (isAdmin(currentUser)) {
            throw new AccessDeniedException("Platform administrators have read-only access to incident diagnostics");
        }
        Incident incident = resolveOwnedIncident(incidentId, currentUser);

        // Row lock acquired only for the duration of this reservation step
        incidentRepository.findByIdForUpdate(incidentId);

        // 1. Concurrency Check: Active in-progress job
        Optional<IncidentAiAnalysis> activeOpt = analysisRepository.findFirstByIncidentIdAndStatusInOrderByVersionDesc(
                incidentId, List.of("PENDING", "PROCESSING")
        );
        if (activeOpt.isPresent()) {
            log.info("Analysis already in progress for incident ID {}. Returning active execution.", incidentId);
            return ReservationResult.alreadyActive(activeOpt.get());
        }

        // 2. Duplicate Prevention: Cached completed analysis
        if (!forceReanalyze) {
            Optional<IncidentAiAnalysis> completedOpt =
                    analysisRepository.findFirstByIncidentIdAndStatusOrderByVersionDesc(incidentId, "COMPLETED");
            if (completedOpt.isPresent()) {
                log.info("Existing completed analysis found for incident ID {}. Returning cached result.", incidentId);
                return ReservationResult.alreadyCompleted(completedOpt.get());
            }
        }

        // 3. Version increment
        int nextVersion = analysisRepository.findFirstByIncidentIdOrderByVersionDesc(incidentId)
                .map(a -> a.getVersion() + 1)
                .orElse(1);

        IncidentAiAnalysis analysis = new IncidentAiAnalysis();
        analysis.setIncident(incident);
        analysis.setStatus("PROCESSING");
        analysis.setVersion(nextVersion);
        analysis.setExecutionId(UUID.randomUUID().toString());
        analysis = analysisRepository.saveAndFlush(analysis);

        // 4. Gather multi-signal telemetry context while transaction is consistent
        Long serviceId = incident.getService() != null ? incident.getService().getId() : null;
        String serviceName = incident.getService() != null ? incident.getService().getName() : "Unknown";

        List<Map<String, Object>> metricsPayload = new ArrayList<>();
        List<Map<String, Object>> alertsPayload = new ArrayList<>();
        List<Map<String, Object>> anomaliesPayload = new ArrayList<>();

        if (serviceId != null) {
            List<ServiceMetric> recentMetrics = metricRepository.findByServiceIdOrderByRecordedAtDescIdDesc(
                    serviceId, PageRequest.of(0, 20)
            );
            for (ServiceMetric m : recentMetrics) {
                Map<String, Object> map = new HashMap<>();
                map.put("metricName", m.getMetricName());
                map.put("metricValue", m.getMetricValue());
                map.put("unit", m.getUnit());
                map.put("recordedAt", m.getRecordedAt() != null ? m.getRecordedAt().toString() : null);
                metricsPayload.add(map);
            }

            List<AlertEvent> activeAlerts = new ArrayList<>();
            activeAlerts.addAll(alertEventRepository.findByServiceIdAndStatusOrderByTriggeredAtDescIdDesc(serviceId, "TRIGGERED"));
            activeAlerts.addAll(alertEventRepository.findByServiceIdAndStatusOrderByTriggeredAtDescIdDesc(serviceId, "ACKNOWLEDGED"));
            for (AlertEvent a : activeAlerts) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", a.getId());
                map.put("severity", a.getSeverity());
                map.put("status", a.getStatus());
                map.put("triggeredAt", a.getTriggeredAt() != null ? a.getTriggeredAt().toString() : null);
                alertsPayload.add(map);
            }

            List<AnomalyEvent> recentAnomalies = anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
                    serviceId, PageRequest.of(0, 10)
            );
            for (AnomalyEvent an : recentAnomalies) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", an.getId());
                map.put("metricName", an.getMetricName());
                map.put("severity", an.getSeverity());
                map.put("anomalyScore", an.getAnomalyScore());
                anomaliesPayload.add(map);
            }
        }

        List<String> logs = new ArrayList<>();
        if (incident.getDescription() != null && !incident.getDescription().trim().isEmpty()) {
            logs.add(incident.getDescription().trim());
        }
        List<IncidentNote> notes = noteRepository.findByIncidentIdOrderByCreatedAtAsc(incident.getId());
        for (IncidentNote n : notes) {
            if (n.getNoteText() != null && !n.getNoteText().trim().isEmpty()) {
                logs.add(n.getNoteText().trim());
            }
        }

        IncidentContext context = new IncidentContext(
                serviceId, serviceName, incident.getTitle(), incident.getDescription(),
                metricsPayload, alertsPayload, anomaliesPayload, logs
        );

        return ReservationResult.proceed(analysis, context);
    }

    /**
     * Short database transaction: records successful COMPLETED diagnosis.
     */
    @Transactional
    public IncidentAiAnalysis recordSuccess(
            Long analysisId,
            String primaryCause,
            String failureCategory,
            Double confidence,
            String evidence,
            String rankedCausesJson,
            String contributingFactorsJson,
            String recommendedRunbooksJson) {
        IncidentAiAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found with id: " + analysisId));

        analysis.setStatus("COMPLETED");
        analysis.setPrimaryRootCause(primaryCause);
        analysis.setFailureCategory(failureCategory);
        analysis.setConfidenceScore(confidence);
        analysis.setPrimaryEvidence(evidence);
        analysis.setRankedCausesJson(rankedCausesJson);
        analysis.setContributingFactorsJson(contributingFactorsJson);
        analysis.setRecommendedRunbooksJson(recommendedRunbooksJson);
        analysis.setCompletedAt(Instant.now());
        analysis.setFailureReason(null);

        return analysisRepository.save(analysis);
    }

    /**
     * Short database transaction: records FAILED diagnosis with explicit failureReason.
     */
    @Transactional
    public IncidentAiAnalysis recordFailure(
            Long analysisId,
            String failureReason,
            String primaryCause,
            String failureCategory) {
        IncidentAiAnalysis analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found with id: " + analysisId));

        analysis.setStatus("FAILED");
        analysis.setFailureReason(failureReason);
        if (primaryCause != null) {
            analysis.setPrimaryRootCause(primaryCause);
        }
        if (failureCategory != null) {
            analysis.setFailureCategory(failureCategory);
        }
        analysis.setCompletedAt(Instant.now());

        return analysisRepository.save(analysis);
    }

    /**
     * Short read-only transaction: constructs rich composite GET response.
     */
    @Transactional(readOnly = true)
    public IncidentAiDiagnosticsResponse getDiagnosticsSummary(Long incidentId, User currentUser) {
        if (incidentId == null) {
            throw new BadRequestException("Incident ID cannot be null");
        }
        resolveOwnedIncident(incidentId, currentUser);

        Optional<IncidentAiAnalysis> latestExecutionOpt =
                analysisRepository.findFirstByIncidentIdOrderByVersionDesc(incidentId);

        if (latestExecutionOpt.isEmpty()) {
            throw new ResourceNotFoundException("No AI diagnostics analysis found for incident ID: " + incidentId);
        }

        IncidentAiAnalysis latestExecution = latestExecutionOpt.get();
        IncidentAiAnalysis lastSuccessful =
                analysisRepository.findFirstByIncidentIdAndStatusOrderByVersionDesc(incidentId, "COMPLETED")
                        .orElse(null);

        return IncidentAiDiagnosticsResponse.of(incidentId, latestExecution, lastSuccessful);
    }

    @Transactional(readOnly = true)
    public IncidentAiAnalysis getLatestAnalysisEntity(Long incidentId, User currentUser) {
        if (incidentId == null) {
            throw new BadRequestException("Incident ID cannot be null");
        }
        resolveOwnedIncident(incidentId, currentUser);

        return analysisRepository.findFirstByIncidentIdAndStatusOrderByVersionDesc(incidentId, "COMPLETED")
                .or(() -> analysisRepository.findFirstByIncidentIdOrderByVersionDesc(incidentId))
                .orElseThrow(() -> new ResourceNotFoundException("No AI diagnostics analysis found for incident ID: " + incidentId));
    }
}
