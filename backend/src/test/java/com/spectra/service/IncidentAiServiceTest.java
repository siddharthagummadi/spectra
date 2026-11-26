package com.spectra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import com.spectra.dto.IncidentAiDiagnosticsResponse;

import com.spectra.entity.AlertEvent;
import com.spectra.entity.AnomalyEvent;
import com.spectra.entity.Incident;
import com.spectra.entity.IncidentAiAnalysis;
import com.spectra.entity.IncidentNote;
import com.spectra.entity.MonitoredService;
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
import com.spectra.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IncidentAiServiceTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private IncidentAiAnalysisRepository analysisRepository;
    @Mock
    private ServiceMetricRepository metricRepository;
    @Mock
    private AlertEventRepository alertEventRepository;
    @Mock
    private AnomalyEventRepository anomalyEventRepository;
    @Mock
    private IncidentNoteRepository noteRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AiServiceClient aiServiceClient;

    private IncidentAiService incidentAiService;

    private Incident testIncident;
    private MonitoredService testService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        incidentAiService = new IncidentAiService(
                incidentRepository,
                analysisRepository,
                metricRepository,
                alertEventRepository,
                anomalyEventRepository,
                noteRepository,
                userRepository,
                aiServiceClient
        );

        testService = new MonitoredService();
        testService.setId(10L);
        testService.setName("Payment Gateway");

        testIncident = new Incident();
        testIncident.setId(100L);
        testIncident.setService(testService);
        testIncident.setTitle("High latency surge");
        testIncident.setDescription("Latency exceeded 3000ms with OutOfMemoryError in logs");
        testIncident.setSeverity("CRITICAL");
        testIncident.setStatus("OPEN");
        testIncident.setTriggeredAt(Instant.now());

        when(incidentRepository.findById(100L)).thenReturn(Optional.of(testIncident));
        when(incidentRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(testIncident));

        java.util.concurrent.atomic.AtomicLong idGenerator = new java.util.concurrent.atomic.AtomicLong(1000L);
        java.util.concurrent.ConcurrentHashMap<Long, IncidentAiAnalysis> analysisDb = new java.util.concurrent.ConcurrentHashMap<>();

        when(analysisRepository.saveAndFlush(any(IncidentAiAnalysis.class))).thenAnswer(inv -> {
            IncidentAiAnalysis a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(idGenerator.incrementAndGet());
            }
            analysisDb.put(a.getId(), a);
            return a;
        });
        when(analysisRepository.save(any(IncidentAiAnalysis.class))).thenAnswer(inv -> {
            IncidentAiAnalysis a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(idGenerator.incrementAndGet());
            }
            analysisDb.put(a.getId(), a);
            return a;
        });
        when(analysisRepository.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return Optional.ofNullable(analysisDb.get(id));
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- 1. Successful RCA and Runbook Orchestration ---

    @Test
    @DisplayName("Successful Analysis: Gathers context, calls Python RCA + RAG, and persists COMPLETED analysis")
    void requestOrRunDiagnostics_SuccessfulOrchestration_PersistsCompleted() {
        // Mock Python RCA response
        Map<String, Object> rcaResult = Map.of(
                "primary_root_cause", "JVM Heap Exhaustion / Memory Leak",
                "failure_category", "MEMORY_LEAK",
                "confidence_score", 0.92,
                "primary_evidence", "Memory usage > 85% with OOM in logs",
                "ranked_causes", List.of(Map.of("cause", "JVM Heap Exhaustion", "confidence", 0.92)),
                "contributing_factors", List.of("Linear memory climb")
        );
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenReturn(rcaResult);

        // Mock Python RAG runbooks response
        Map<String, Object> runbooksResult = Map.of(
                "matches", List.of(Map.of("runbook_id", "RB-002", "title", "Memory Leak Mitigation"))
        );
        when(aiServiceClient.queryRunbooks(anyString(), anyInt(), any())).thenReturn(runbooksResult);

        IncidentAiAnalysis result = incidentAiService.requestOrRunDiagnostics(100L, false);

        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        assertEquals("JVM Heap Exhaustion / Memory Leak", result.getPrimaryRootCause());
        assertEquals("MEMORY_LEAK", result.getFailureCategory());
        assertEquals(0.92, result.getConfidenceScore());
        assertEquals(1, result.getVersion());
        assertNotNull(result.getExecutionId());
        assertNotNull(result.getCompletedAt());

        // Verify orchestration calls were made
        verify(aiServiceClient).analyzeRootCause(eq(10L), eq("Payment Gateway"), eq("High latency surge"), any(), anyList(), anyList(), anyList());
        verify(aiServiceClient).queryRunbooks(eq("JVM Heap Exhaustion / Memory Leak"), eq(3), any());
        verify(analysisRepository).save(any(IncidentAiAnalysis.class));
    }

    // --- 2. GET Retrieves Stored Result Without Invoking Python ---

    @Test
    @DisplayName("GET Latest Analysis: Returns stored analysis without calling Python microservice")
    void getLatestAnalysis_ReturnsPersistedWithoutCallingPython() {
        IncidentAiAnalysis stored = new IncidentAiAnalysis();
        stored.setId(500L);
        stored.setIncident(testIncident);
        stored.setStatus("COMPLETED");
        stored.setPrimaryRootCause("Database Pool Exhaustion");
        stored.setConfidenceScore(0.85);
        stored.setVersion(1);

        when(analysisRepository.findFirstByIncidentIdAndStatusOrderByVersionDesc(100L, "COMPLETED"))
                .thenReturn(Optional.of(stored));

        IncidentAiAnalysis result = incidentAiService.getLatestAnalysis(100L);

        assertNotNull(result);
        assertEquals(500L, result.getId());
        assertEquals("Database Pool Exhaustion", result.getPrimaryRootCause());

        // Verify AiServiceClient was NEVER contacted during GET
        verify(aiServiceClient, never()).analyzeRootCause(any(), any(), any(), any(), any(), any(), any());
        verify(aiServiceClient, never()).queryRunbooks(any(), anyInt(), any());
    }

    // --- 3. Duplicate Prevention (Active Job in Progress) ---

    @Test
    @DisplayName("Duplicate Prevention: Active in-progress analysis returns existing record without starting new job")
    void requestOrRunDiagnostics_ActiveJobInProgress_ReturnsExistingRecord() {
        IncidentAiAnalysis inProgress = new IncidentAiAnalysis();
        inProgress.setId(999L);
        inProgress.setStatus("PROCESSING");
        inProgress.setVersion(1);

        when(analysisRepository.existsByIncidentIdAndStatusIn(100L, List.of("PENDING", "PROCESSING"))).thenReturn(true);
        when(analysisRepository.findFirstByIncidentIdAndStatusInOrderByVersionDesc(100L, List.of("PENDING", "PROCESSING")))
                .thenReturn(Optional.of(inProgress));

        IncidentAiAnalysis result = incidentAiService.requestOrRunDiagnostics(100L, false);

        assertEquals(999L, result.getId());
        assertEquals("PROCESSING", result.getStatus());

        // Verify no Python calls initiated
        verify(aiServiceClient, never()).analyzeRootCause(any(), any(), any(), any(), any(), any(), any());
    }

    // --- 4. Duplicate Prevention (Completed Analysis Exists) ---

    @Test
    @DisplayName("Duplicate Prevention: Completed analysis returns cached record when forceReanalyze is false")
    void requestOrRunDiagnostics_CompletedAnalysisExists_ReturnsCachedRecord() {
        IncidentAiAnalysis completed = new IncidentAiAnalysis();
        completed.setId(888L);
        completed.setStatus("COMPLETED");
        completed.setPrimaryRootCause("Network Partition");
        completed.setVersion(1);

        when(analysisRepository.findFirstByIncidentIdAndStatusOrderByVersionDesc(100L, "COMPLETED"))
                .thenReturn(Optional.of(completed));

        IncidentAiAnalysis result = incidentAiService.requestOrRunDiagnostics(100L, false);

        assertEquals(888L, result.getId());
        assertEquals("COMPLETED", result.getStatus());
        assertEquals("Network Partition", result.getPrimaryRootCause());

        // Verify no Python calls initiated
        verify(aiServiceClient, never()).analyzeRootCause(any(), any(), any(), any(), any(), any(), any());
    }

    // --- 5. Manual Re-Analysis (Force Re-analyze = true) ---

    @Test
    @DisplayName("Manual Re-Analysis: forceReanalyze=true increments version and triggers new analysis")
    void requestOrRunDiagnostics_ForceReanalyze_IncrementsVersion() {
        IncidentAiAnalysis v1 = new IncidentAiAnalysis();
        v1.setId(101L);
        v1.setStatus("COMPLETED");
        v1.setPrimaryRootCause("Previous Diagnosis");
        v1.setVersion(1);

        when(analysisRepository.findFirstByIncidentIdOrderByVersionDesc(100L)).thenReturn(Optional.of(v1));

        Map<String, Object> rcaResult = Map.of(
                "primary_root_cause", "Updated Diagnosis: Thread Lock",
                "failure_category", "CPU_STARVATION",
                "confidence_score", 0.89,
                "primary_evidence", "Thread deadlock detected"
        );
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenReturn(rcaResult);
        when(aiServiceClient.queryRunbooks(anyString(), anyInt(), any())).thenReturn(Map.of("matches", List.of()));

        IncidentAiAnalysis result = incidentAiService.requestOrRunDiagnostics(100L, true);

        assertNotNull(result);
        assertEquals(2, result.getVersion(), "New analysis must increment version to 2");
        assertEquals("COMPLETED", result.getStatus());
        assertEquals("Updated Diagnosis: Thread Lock", result.getPrimaryRootCause());
    }

    // --- 6. Previous Successful Result Preserved on Failed Re-Analysis ---

    @Test
    @DisplayName("Preservation: If re-analysis fails, previous completed result remains accessible")
    void requestOrRunDiagnostics_FailedReanalysis_PreservesPreviousCompletedResult() {
        // Version 1 was completed
        IncidentAiAnalysis v1 = new IncidentAiAnalysis();
        v1.setId(101L);
        v1.setStatus("COMPLETED");
        v1.setPrimaryRootCause("V1 Stable Diagnosis");
        v1.setVersion(1);

        when(analysisRepository.findFirstByIncidentIdOrderByVersionDesc(100L)).thenReturn(Optional.of(v1));
        when(analysisRepository.findFirstByIncidentIdAndStatusOrderByVersionDesc(100L, "COMPLETED")).thenReturn(Optional.of(v1));

        // Re-analysis (V2) fails because Python is down / throws exception
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenThrow(new RuntimeException("Connection refused to AI microservice"));

        IncidentAiAnalysis failedV2 = incidentAiService.requestOrRunDiagnostics(100L, true);

        assertEquals("FAILED", failedV2.getStatus());
        assertEquals(2, failedV2.getVersion());
        assertTrue(failedV2.getFailureReason().contains("AI microservice invocation failed"));

        // When user calls GET, V1 is still returned as the latest COMPLETED analysis!
        IncidentAiAnalysis retrieved = incidentAiService.getLatestAnalysis(100L);
        assertEquals(101L, retrieved.getId());
        assertEquals("V1 Stable Diagnosis", retrieved.getPrimaryRootCause());
        assertEquals("COMPLETED", retrieved.getStatus());
    }

    // --- 7. Offline Fallback Detection Marks Analysis FAILED ---

    @Test
    @DisplayName("Offline Fallback: Python offline response marks status as FAILED with descriptive reason")
    void requestOrRunDiagnostics_PythonOfflineFallback_MarksFailed() {
        Map<String, Object> offlineFallback = Map.of(
                "primary_root_cause", "Deterministic Fallback: Anomaly in service metrics",
                "failure_category", "GENERAL_ANOMALY",
                "primary_evidence", "AI Service offline; fallback rule triggered"
        );
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenReturn(offlineFallback);

        IncidentAiAnalysis result = incidentAiService.requestOrRunDiagnostics(100L, false);

        assertNotNull(result);
        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getFailureReason().contains("AI microservice is unavailable"));
        assertNotNull(result.getCompletedAt());
    }

    // --- 8. Validation and Non-Existent Incident ---

    @Test
    @DisplayName("Validation: Null incident ID throws BadRequestException")
    void requestOrRunDiagnostics_NullIncidentId_ThrowsBadRequest() {
        assertThrows(BadRequestException.class, () -> incidentAiService.requestOrRunDiagnostics(null, false));
    }

    @Test
    @DisplayName("Non-Existent Incident: Throws ResourceNotFoundException")
    void requestOrRunDiagnostics_NonExistentIncident_ThrowsNotFound() {
        when(incidentRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> incidentAiService.requestOrRunDiagnostics(999L, false));
    }

    // --- 9. Stage 2.2.1 Hardening Tests ---

    @Test
    @DisplayName("RCA Validation: Empty RCA response marks analysis FAILED")
    void requestOrRunDiagnostics_EmptyRcaResponse_MarksFailed() {
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenReturn(Collections.emptyMap());

        IncidentAiAnalysis result = incidentAiService.requestOrRunDiagnostics(100L, false);

        assertNotNull(result);
        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getFailureReason().contains("empty or null RCA response"));
    }

    @Test
    @DisplayName("RCA Validation: Missing or blank primary_root_cause marks analysis FAILED")
    void requestOrRunDiagnostics_MissingPrimaryRootCause_MarksFailed() {
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenReturn(Map.of("failure_category", "MEMORY_LEAK", "primary_root_cause", "   "));

        IncidentAiAnalysis result = incidentAiService.requestOrRunDiagnostics(100L, false);

        assertNotNull(result);
        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getFailureReason().contains("missing or empty 'primary_root_cause'"));
    }

    @Test
    @DisplayName("RCA Validation: Malformed ranked_causes structure marks analysis FAILED")
    void requestOrRunDiagnostics_MalformedRcaResponse_MarksFailed() {
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenReturn(Map.of("primary_root_cause", "Disk Full", "ranked_causes", "invalid-not-a-list"));

        IncidentAiAnalysis result = incidentAiService.requestOrRunDiagnostics(100L, false);

        assertNotNull(result);
        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getFailureReason().contains("'ranked_causes' is not a list"));
    }

    @Test
    @DisplayName("RAG Validation: Invalid or empty RAG response marks analysis FAILED")
    void requestOrRunDiagnostics_InvalidOrEmptyRagResponse_MarksFailed() {
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenReturn(Map.of("primary_root_cause", "Network Timeout"));

        // Case A: null RAG response
        when(aiServiceClient.queryRunbooks(anyString(), anyInt(), any())).thenReturn(null);
        IncidentAiAnalysis resultNull = incidentAiService.requestOrRunDiagnostics(100L, false);
        assertEquals("FAILED", resultNull.getStatus());
        assertTrue(resultNull.getFailureReason().contains("Runbook retrieval returned null or empty response"));

        // Case B: malformed matches in RAG response
        when(aiServiceClient.queryRunbooks(anyString(), anyInt(), any())).thenReturn(Map.of("matches", "malformed-not-a-list"));
        IncidentAiAnalysis resultMalformed = incidentAiService.requestOrRunDiagnostics(100L, true);
        assertEquals("FAILED", resultMalformed.getStatus());
        assertTrue(resultMalformed.getFailureReason().contains("missing or malformed 'matches' list"));
    }

    @Test
    @DisplayName("Preservation: Previous successful version retained after invalid re-analysis response")
    void requestOrRunDiagnostics_InvalidReanalysis_RetainsPreviousSuccessfulVersion() {
        IncidentAiAnalysis v1 = new IncidentAiAnalysis();
        v1.setId(101L);
        v1.setStatus("COMPLETED");
        v1.setPrimaryRootCause("V1 Stable Diagnosis");
        v1.setVersion(1);

        when(analysisRepository.findFirstByIncidentIdOrderByVersionDesc(100L)).thenReturn(Optional.of(v1));
        when(analysisRepository.findFirstByIncidentIdAndStatusOrderByVersionDesc(100L, "COMPLETED")).thenReturn(Optional.of(v1));

        // Re-analysis returns missing primary root cause
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenReturn(Map.of("failure_category", "UNKNOWN"));

        IncidentAiAnalysis failedV2 = incidentAiService.requestOrRunDiagnostics(100L, true);

        assertEquals("FAILED", failedV2.getStatus());
        assertEquals(2, failedV2.getVersion());

        // Calling GET still retrieves Version 1 COMPLETED
        IncidentAiAnalysis retrieved = incidentAiService.getLatestAnalysis(100L);
        assertEquals(101L, retrieved.getId());
        assertEquals("COMPLETED", retrieved.getStatus());
        assertEquals("V1 Stable Diagnosis", retrieved.getPrimaryRootCause());
    }

    @Test
    @DisplayName("Authorization: Authenticated but unresolved user is rejected with AccessDeniedException")
    void authorization_AuthenticatedUnresolvedUser_ThrowsAccessDenied() {
        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "ghost@spectra.local", "password",
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
                );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            when(userRepository.findByEmail("ghost@spectra.local")).thenReturn(Optional.empty());

            assertThrows(AccessDeniedException.class, () -> incidentAiService.getLatestAnalysis(100L));
            assertThrows(AccessDeniedException.class, () -> incidentAiService.requestOrRunDiagnostics(100L, false));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Authorization: Legitimate USER access and ADMIN access are preserved")
    void authorization_AdminAndLegitimateUserAccess_Preserved() {
        // Legitimate USER accessing own incident
        User operator = new User();
        operator.setId(5L);
        operator.setRole(Role.USER);

        org.springframework.security.core.Authentication userAuth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "operator@spectra.local", "password",
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
                );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(userAuth);
        try {
            when(userRepository.findByEmail("operator@spectra.local")).thenReturn(Optional.of(operator));
            when(incidentRepository.findByIdAndServiceUserId(100L, 5L)).thenReturn(Optional.of(testIncident));
            when(analysisRepository.findFirstByIncidentIdAndStatusOrderByVersionDesc(100L, "COMPLETED"))
                    .thenReturn(Optional.of(new IncidentAiAnalysis()));

            IncidentAiAnalysis res = incidentAiService.getLatestAnalysis(100L);
            assertNotNull(res);
            verify(incidentRepository).findByIdAndServiceUserId(100L, 5L);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        // ADMIN accessing any incident
        User admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);

        org.springframework.security.core.Authentication adminAuth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "admin@spectra.local", "password",
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
                );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(adminAuth);
        try {
            when(userRepository.findByEmail("admin@spectra.local")).thenReturn(Optional.of(admin));
            when(incidentRepository.findById(100L)).thenReturn(Optional.of(testIncident));
            when(analysisRepository.findFirstByIncidentIdAndStatusOrderByVersionDesc(100L, "COMPLETED"))
                    .thenReturn(Optional.of(new IncidentAiAnalysis()));

            IncidentAiAnalysis res = incidentAiService.getLatestAnalysis(100L);
            assertNotNull(res);
            verify(incidentRepository, times(1)).findById(100L);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("Authorization: Cross-tenant USER access is rejected with ResourceNotFoundException")
    void authorization_CrossTenantUserAccess_ThrowsNotFound() {
        User otherUser = new User();
        otherUser.setId(99L);
        otherUser.setRole(Role.USER);

        org.springframework.security.core.Authentication userAuth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "other@spectra.local", "password",
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
                );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(userAuth);
        try {
            when(userRepository.findByEmail("other@spectra.local")).thenReturn(Optional.of(otherUser));
            when(incidentRepository.findByIdAndServiceUserId(100L, 99L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> incidentAiService.getLatestAnalysis(100L));
            assertThrows(ResourceNotFoundException.class, () -> incidentAiService.requestOrRunDiagnostics(100L, false));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    // --- 10. Stage 2.2.2 Composite GET Contract Tests ---

    @Test
    @DisplayName("Composite GET: Returns both latest execution and last successful analysis")
    void getDiagnosticsSummary_Completed_ReturnsBoth() {
        IncidentAiAnalysis completed = new IncidentAiAnalysis();
        completed.setId(201L);
        completed.setStatus("COMPLETED");
        completed.setVersion(1);
        completed.setPrimaryRootCause("Disk Saturation");

        when(analysisRepository.findFirstByIncidentIdOrderByVersionDesc(100L)).thenReturn(Optional.of(completed));
        when(analysisRepository.findFirstByIncidentIdAndStatusOrderByVersionDesc(100L, "COMPLETED")).thenReturn(Optional.of(completed));

        IncidentAiDiagnosticsResponse response = incidentAiService.getDiagnosticsSummary(100L);

        assertNotNull(response);
        assertEquals(100L, response.incidentId());
        assertNotNull(response.latestExecution());
        assertNotNull(response.lastSuccessful());
        assertEquals("Disk Saturation", response.latestExecution().primaryRootCause());
        assertEquals("Disk Saturation", response.lastSuccessful().primaryRootCause());
    }

    @Test
    @DisplayName("Composite GET: Re-analysis failed exposes latest FAILED execution and previous COMPLETED diagnosis")
    void getDiagnosticsSummary_FailedWithPreviousCompleted_ExposesBoth() {
        IncidentAiAnalysis v1 = new IncidentAiAnalysis();
        v1.setId(201L);
        v1.setStatus("COMPLETED");
        v1.setVersion(1);
        v1.setPrimaryRootCause("V1 Good");

        IncidentAiAnalysis v2 = new IncidentAiAnalysis();
        v2.setId(202L);
        v2.setStatus("FAILED");
        v2.setVersion(2);
        v2.setFailureReason("Python service timeout");

        when(analysisRepository.findFirstByIncidentIdOrderByVersionDesc(100L)).thenReturn(Optional.of(v2));
        when(analysisRepository.findFirstByIncidentIdAndStatusOrderByVersionDesc(100L, "COMPLETED")).thenReturn(Optional.of(v1));

        IncidentAiDiagnosticsResponse response = incidentAiService.getDiagnosticsSummary(100L);

        assertNotNull(response);
        assertEquals("FAILED", response.latestExecution().status());
        assertEquals("Python service timeout", response.latestExecution().failureReason());
        assertNotNull(response.lastSuccessful());
        assertEquals("COMPLETED", response.lastSuccessful().status());
        assertEquals("V1 Good", response.lastSuccessful().primaryRootCause());
    }

    @Test
    @DisplayName("Composite GET: Processing with no prior success returns null for lastSuccessful")
    void getDiagnosticsSummary_ProcessingNoPriorSuccess_ReturnsNullLastSuccessful() {
        IncidentAiAnalysis processing = new IncidentAiAnalysis();
        processing.setId(203L);
        processing.setStatus("PROCESSING");
        processing.setVersion(1);

        when(analysisRepository.findFirstByIncidentIdOrderByVersionDesc(100L)).thenReturn(Optional.of(processing));
        when(analysisRepository.findFirstByIncidentIdAndStatusOrderByVersionDesc(100L, "COMPLETED")).thenReturn(Optional.empty());

        IncidentAiDiagnosticsResponse response = incidentAiService.getDiagnosticsSummary(100L);

        assertNotNull(response);
        assertEquals("PROCESSING", response.latestExecution().status());
        assertNull(response.lastSuccessful());
    }

    @Test
    @DisplayName("Composite GET: No analysis exists throws ResourceNotFoundException (404)")
    void getDiagnosticsSummary_NoAnalysis_ThrowsNotFound() {
        when(analysisRepository.findFirstByIncidentIdOrderByVersionDesc(100L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> incidentAiService.getDiagnosticsSummary(100L));
    }
}
