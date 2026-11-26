package com.spectra.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.spectra.dto.IncidentAiDiagnosticsResponse;
import com.spectra.entity.Incident;
import com.spectra.entity.IncidentAiAnalysis;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.IncidentAiAnalysisRepository;
import com.spectra.repository.IncidentRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.UserRepository;
import com.spectra.service.AiServiceClient;
import com.spectra.service.IncidentAiService;

@SpringBootTest
@ActiveProfiles("test")
public class IncidentAiDiagnosticsIntegrationTest {

    @Autowired
    private IncidentAiService incidentAiService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentAiAnalysisRepository analysisRepository;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private AiServiceClient aiServiceClient;

    private User ownerUser;
    private User adminUser;
    private User otherUser;
    private MonitoredService testService;
    private Incident testIncident;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        // 1. Create owner user
        ownerUser = new User();
        ownerUser.setEmail("owner-" + System.nanoTime() + "@spectra.local");
        ownerUser.setFullName("Service Owner");
        ownerUser.setPassword("password123");
        ownerUser.setRole(Role.USER);
        ownerUser = userRepository.save(ownerUser);

        // 2. Create admin user
        adminUser = new User();
        adminUser.setEmail("admin-" + System.nanoTime() + "@spectra.local");
        adminUser.setFullName("Platform Admin");
        adminUser.setPassword("password123");
        adminUser.setRole(Role.ADMIN);
        adminUser = userRepository.save(adminUser);

        // 3. Create other user (cross-tenant)
        otherUser = new User();
        otherUser.setEmail("other-" + System.nanoTime() + "@spectra.local");
        otherUser.setFullName("Other Tenant");
        otherUser.setPassword("password123");
        otherUser.setRole(Role.USER);
        otherUser = userRepository.save(otherUser);

        // 4. Create monitored service
        testService = new MonitoredService();
        testService.setUser(ownerUser);
        testService.setName("Order Processing Service");
        testService.setDescription("Handles order pipeline");
        testService.setEndpointUrl("https://orders.spectra.internal/health");
        testService.setEnvironment("PRODUCTION");
        testService.setActive(true);
        testService = serviceRepository.save(testService);

        // 5. Create incident
        testIncident = new Incident();
        testIncident.setService(testService);
        testIncident.setTitle("Order Processing Gateway Timeout 504");
        testIncident.setDescription("Latency climbed past 5000ms and requests started failing");
        testIncident.setSeverity("CRITICAL");
        testIncident.setStatus("OPEN");
        testIncident.setTriggeredAt(Instant.now());
        testIncident = incidentRepository.save(testIncident);

        // Default mock responses for AI microservice
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenReturn(Map.of(
                        "primary_root_cause", "Database Connection Pool Exhaustion",
                        "failure_category", "RESOURCE_EXHAUSTION",
                        "confidence_score", 0.94,
                        "primary_evidence", "HikariCP active connections = 100, pending queries queued",
                        "ranked_causes", List.of(Map.of("cause", "Database Connection Pool Exhaustion", "confidence", 0.94)),
                        "contributing_factors", List.of("Spike in unindexed reporting queries")
                ));

        when(aiServiceClient.queryRunbooks(anyString(), anyInt(), any()))
                .thenReturn(Map.of(
                        "matches", List.of(Map.of("runbook_id", "RB-DB-01", "title", "Expand Hikari Pool and Terminate Stale Queries"))
                ));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        analysisRepository.deleteAll();
        incidentRepository.deleteAll();
        serviceRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        "password123",
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                )
        );
    }

    // --- 1. Row Lock Released Before Outbound Network Calls & Reservation Committed ---

    @Test
    @DisplayName("Transaction Boundaries: Row lock is released before Python network calls, allowing concurrent updates")
    void testRowLockReleasedBeforePythonNetworkCalls() throws Exception {
        authenticateAs(ownerUser);

        CountDownLatch pythonCallStarted = new CountDownLatch(1);
        CountDownLatch concurrentVerificationCompleted = new CountDownLatch(1);
        AtomicBoolean concurrentUpdateSucceeded = new AtomicBoolean(false);
        AtomicBoolean reservationVisibleInDb = new AtomicBoolean(false);

        // Intercept AI call to simulate slow external network call and verify DB state concurrently
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenAnswer(inv -> {
                    pythonCallStarted.countDown();
                    // Wait for concurrent thread to verify lock release
                    boolean finishedInTime = concurrentVerificationCompleted.await(5, TimeUnit.SECONDS);
                    if (!finishedInTime) {
                        throw new IllegalStateException("Timeout waiting for concurrent lock verification");
                    }
                    return Map.of(
                            "primary_root_cause", "Database Connection Pool Exhaustion",
                            "failure_category", "RESOURCE_EXHAUSTION",
                            "confidence_score", 0.94,
                            "primary_evidence", "HikariCP connection pool starvation",
                            "ranked_causes", List.of(Map.of("cause", "Pool exhaustion", "confidence", 0.94)),
                            "contributing_factors", List.of("Slow query accumulation")
                    );
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Thread A: calls incidentAiService (which reserves in short TX, releases lock, calls Python)
            Future<IncidentAiAnalysis> aiFuture = executor.submit(() -> incidentAiService.requestOrRunDiagnostics(testIncident.getId(), false));

            // Wait until Thread A has reached the outbound Python call
            assertTrue(pythonCallStarted.await(5, TimeUnit.SECONDS), "AI client should be reached");

            // Thread B: concurrently verifies that:
            // 1. The reservation is ALREADY committed in the DB with status PROCESSING.
            // 2. The row lock on Incident is NOT held, so Thread B can lock/update the Incident row immediately.
            Future<?> concurrentFuture = executor.submit(() -> {
                TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
                txTemplate.execute(status -> {
                    // Check reservation record in DB
                    Optional<IncidentAiAnalysis> activeAnalysis =
                            analysisRepository.findFirstByIncidentIdAndStatusInOrderByVersionDesc(testIncident.getId(), List.of("PROCESSING"));
                    if (activeAnalysis.isPresent() && "PROCESSING".equals(activeAnalysis.get().getStatus())) {
                        reservationVisibleInDb.set(true);
                    }

                    // Attempt to acquire lock on Incident row
                    Optional<Incident> lockedIncident = incidentRepository.findByIdForUpdate(testIncident.getId());
                    if (lockedIncident.isPresent()) {
                        Incident inc = lockedIncident.get();
                        inc.setDescription("Updated concurrently while Python call is active");
                        incidentRepository.save(inc);
                        concurrentUpdateSucceeded.set(true);
                    }
                    return null;
                });
                concurrentVerificationCompleted.countDown();
            });

            concurrentFuture.get(5, TimeUnit.SECONDS);
            IncidentAiAnalysis finalResult = aiFuture.get(5, TimeUnit.SECONDS);

            // Assertions
            assertTrue(reservationVisibleInDb.get(), "Reservation must be committed with status PROCESSING before Python call");
            assertTrue(concurrentUpdateSucceeded.get(), "Concurrent transaction must acquire incident row lock without blocking");
            assertNotNull(finalResult);
            assertEquals("COMPLETED", finalResult.getStatus());
            assertEquals("Database Connection Pool Exhaustion", finalResult.getPrimaryRootCause());

            // Verify the concurrent description update persisted
            Incident refreshedIncident = incidentRepository.findById(testIncident.getId()).orElseThrow();
            assertEquals("Updated concurrently while Python call is active", refreshedIncident.getDescription());
        } finally {
            executor.shutdownNow();
        }
    }

    // --- 2. Concurrency: Deduplication of Active Executions ---

    @Test
    @DisplayName("Concurrency: Concurrent requests do not create duplicate active executions")
    void testConcurrentRequests_DoNotCreateDuplicateActiveExecutions() throws Exception {
        authenticateAs(ownerUser);

        CountDownLatch pythonEntered = new CountDownLatch(1);
        CountDownLatch allowPythonToComplete = new CountDownLatch(1);

        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenAnswer(inv -> {
                    pythonEntered.countDown();
                    allowPythonToComplete.await(5, TimeUnit.SECONDS);
                    return Map.of(
                            "primary_root_cause", "Database Connection Pool Exhaustion",
                            "failure_category", "RESOURCE_EXHAUSTION",
                            "confidence_score", 0.94,
                            "primary_evidence", "HikariCP active connections = 100",
                            "ranked_causes", List.of(Map.of("cause", "Pool exhaustion", "confidence", 0.94)),
                            "contributing_factors", List.of("Spike in unindexed reporting queries")
                    );
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Request 1 starts analysis
            Future<IncidentAiAnalysis> future1 = executor.submit(() -> incidentAiService.requestOrRunDiagnostics(testIncident.getId(), false));

            // Wait until Request 1 is inside the Python call (status is PROCESSING)
            assertTrue(pythonEntered.await(5, TimeUnit.SECONDS));

            // Request 2 arrives concurrently
            Future<IncidentAiAnalysis> future2 = executor.submit(() -> incidentAiService.requestOrRunDiagnostics(testIncident.getId(), false));

            IncidentAiAnalysis res2 = future2.get(5, TimeUnit.SECONDS);
            // Request 2 should immediately see the active PROCESSING execution
            assertEquals("PROCESSING", res2.getStatus());

            // Release Request 1 to complete
            allowPythonToComplete.countDown();
            IncidentAiAnalysis res1 = future1.get(5, TimeUnit.SECONDS);
            assertEquals("COMPLETED", res1.getStatus());

            // Verify only ONE analysis row was created in total for this incident
            List<IncidentAiAnalysis> allAnalyses = analysisRepository.findAll();
            assertEquals(1, allAnalyses.size(), "Only one persistent analysis record should exist; no duplicate active jobs");
        } finally {
            executor.shutdownNow();
        }
    }

    // --- 3. Version Assignment Consistency ---

    @Test
    @DisplayName("Version Consistency: Incremental versions are assigned sequentially across re-analyses")
    void testVersionAssignment_RemainsConsistent() {
        authenticateAs(ownerUser);

        // Execution 1
        IncidentAiAnalysis v1 = incidentAiService.requestOrRunDiagnostics(testIncident.getId(), false);
        assertEquals(1, v1.getVersion());
        assertEquals("COMPLETED", v1.getStatus());

        // Re-analysis 1 (forceReanalyze = true)
        IncidentAiAnalysis v2 = incidentAiService.requestOrRunDiagnostics(testIncident.getId(), true);
        assertEquals(2, v2.getVersion());
        assertEquals("COMPLETED", v2.getStatus());

        // Re-analysis 2 (forceReanalyze = true)
        IncidentAiAnalysis v3 = incidentAiService.requestOrRunDiagnostics(testIncident.getId(), true);
        assertEquals(3, v3.getVersion());
        assertEquals("COMPLETED", v3.getStatus());

        // Verify in DB
        Optional<IncidentAiAnalysis> latest = analysisRepository.findFirstByIncidentIdOrderByVersionDesc(testIncident.getId());
        assertTrue(latest.isPresent());
        assertEquals(3, latest.get().getVersion());
    }

    // --- 4. Successful Execution Full Persistence ---

    @Test
    @DisplayName("Persistence: Successful execution persists full RCA, RAG, and execution metadata")
    void testSuccessfulExecution_PersistsAllMetadata() {
        authenticateAs(ownerUser);

        IncidentAiAnalysis result = incidentAiService.requestOrRunDiagnostics(testIncident.getId(), false);

        assertNotNull(result.getId());
        assertEquals("COMPLETED", result.getStatus());
        assertEquals("Database Connection Pool Exhaustion", result.getPrimaryRootCause());
        assertEquals("RESOURCE_EXHAUSTION", result.getFailureCategory());
        assertEquals(0.94, result.getConfidenceScore());
        assertEquals("HikariCP active connections = 100, pending queries queued", result.getPrimaryEvidence());
        assertNotNull(result.getRankedCausesJson());
        assertTrue(result.getRankedCausesJson().contains("Database Connection Pool Exhaustion"));
        assertNotNull(result.getContributingFactorsJson());
        assertTrue(result.getContributingFactorsJson().contains("Spike in unindexed reporting queries"));
        assertNotNull(result.getRecommendedRunbooksJson());
        assertTrue(result.getRecommendedRunbooksJson().contains("RB-DB-01"));
        assertNull(result.getFailureReason());
        assertNotNull(result.getExecutionId());
        assertNotNull(result.getCreatedAt());
        assertNotNull(result.getCompletedAt());
    }

    // --- 5. Failed Re-analysis Preserves Previous Successful Result ---

    @Test
    @DisplayName("Preservation: Failed re-analysis does not corrupt or overwrite previous completed diagnosis")
    void testFailedReanalysis_PreservesPreviousSuccessfulExecution() {
        authenticateAs(ownerUser);

        // V1 succeeds
        IncidentAiAnalysis v1 = incidentAiService.requestOrRunDiagnostics(testIncident.getId(), false);
        assertEquals("COMPLETED", v1.getStatus());
        assertEquals(1, v1.getVersion());

        // Simulate Python failure on re-analysis
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenThrow(new RuntimeException("Simulated Python microservice 503 Unavailable"));

        IncidentAiAnalysis v2 = incidentAiService.requestOrRunDiagnostics(testIncident.getId(), true);
        assertEquals("FAILED", v2.getStatus());
        assertEquals(2, v2.getVersion());
        assertTrue(v2.getFailureReason().contains("AI microservice invocation failed"));

        // Verify V1 is intact in database
        Optional<IncidentAiAnalysis> retrievedV1 = analysisRepository.findFirstByIncidentIdAndStatusOrderByVersionDesc(testIncident.getId(), "COMPLETED");
        assertTrue(retrievedV1.isPresent());
        assertEquals(1, retrievedV1.get().getVersion());
        assertEquals("COMPLETED", retrievedV1.get().getStatus());
        assertEquals("Database Connection Pool Exhaustion", retrievedV1.get().getPrimaryRootCause());
    }

    // --- 6. Composite GET Contract: Latest FAILED and Previous COMPLETED ---

    @Test
    @DisplayName("Composite GET: Exposes both latest FAILED execution and previous COMPLETED diagnosis")
    void testCompositeGet_ReturnsLatestFailedAndPreviousCompleted() {
        authenticateAs(ownerUser);

        // V1 COMPLETED
        incidentAiService.requestOrRunDiagnostics(testIncident.getId(), false);

        // V2 FAILED
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenThrow(new RuntimeException("Connection timed out"));
        incidentAiService.requestOrRunDiagnostics(testIncident.getId(), true);

        // Call GET composite summary
        IncidentAiDiagnosticsResponse summary = incidentAiService.getDiagnosticsSummary(testIncident.getId());

        assertNotNull(summary);
        assertEquals(testIncident.getId(), summary.incidentId());

        // Latest execution is V2 FAILED
        assertNotNull(summary.latestExecution());
        assertEquals(2, summary.latestExecution().version());
        assertEquals("FAILED", summary.latestExecution().status());
        assertTrue(summary.latestExecution().failureReason().contains("Connection timed out"));

        // Last successful is V1 COMPLETED
        assertNotNull(summary.lastSuccessful());
        assertEquals(1, summary.lastSuccessful().version());
        assertEquals("COMPLETED", summary.lastSuccessful().status());
        assertEquals("Database Connection Pool Exhaustion", summary.lastSuccessful().primaryRootCause());
    }

    // --- 7. Composite GET Contract: Latest COMPLETED Execution ---

    @Test
    @DisplayName("Composite GET: When latest execution is COMPLETED, latestExecution and lastSuccessful are identical")
    void testCompositeGet_ReturnsCompletedExecution() {
        authenticateAs(ownerUser);

        incidentAiService.requestOrRunDiagnostics(testIncident.getId(), false);

        IncidentAiDiagnosticsResponse summary = incidentAiService.getDiagnosticsSummary(testIncident.getId());

        assertNotNull(summary);
        assertNotNull(summary.latestExecution());
        assertNotNull(summary.lastSuccessful());
        assertEquals(summary.latestExecution().id(), summary.lastSuccessful().id());
        assertEquals("COMPLETED", summary.latestExecution().status());
        assertEquals("Database Connection Pool Exhaustion", summary.latestExecution().primaryRootCause());
    }

    // --- 8. Composite GET Contract: Only PROCESSING with No Prior Success ---

    @Test
    @DisplayName("Composite GET: When only PROCESSING exists, latestExecution is PROCESSING and lastSuccessful is null")
    void testCompositeGet_OnlyProcessingWithNoPriorSuccess() {
        authenticateAs(ownerUser);

        // Save a PROCESSING analysis directly
        IncidentAiAnalysis inFlight = new IncidentAiAnalysis();
        inFlight.setIncident(testIncident);
        inFlight.setStatus("PROCESSING");
        inFlight.setVersion(1);
        inFlight.setExecutionId("exec-inflight-123");
        analysisRepository.save(inFlight);

        IncidentAiDiagnosticsResponse summary = incidentAiService.getDiagnosticsSummary(testIncident.getId());

        assertNotNull(summary);
        assertEquals("PROCESSING", summary.latestExecution().status());
        assertNull(summary.lastSuccessful());
    }

    // --- 9. Composite GET Contract: No Analysis Exists (404) ---

    @Test
    @DisplayName("Composite GET: When no analysis exists for incident, throws ResourceNotFoundException (404)")
    void testCompositeGet_NoAnalysis_ThrowsNotFound() {
        authenticateAs(ownerUser);

        assertThrows(ResourceNotFoundException.class, () -> incidentAiService.getDiagnosticsSummary(testIncident.getId()));
    }

    // --- 10. Authorization: Legitimate User, Admin, and Cross-Tenant Rejection ---

    @Test
    @DisplayName("Authorization: Legitimate user and Admin have access; cross-tenant request is rejected")
    void testAuthorization_UserAndAdminAllowed_CrossTenantRejected() {
        // 1. Initial creation by legitimate user
        authenticateAs(ownerUser);
        incidentAiService.requestOrRunDiagnostics(testIncident.getId(), false);

        // 2. Legitimate user can read diagnostics
        IncidentAiDiagnosticsResponse userResponse = incidentAiService.getDiagnosticsSummary(testIncident.getId());
        assertNotNull(userResponse);

        // 3. Admin user can read diagnostics
        authenticateAs(adminUser);
        IncidentAiDiagnosticsResponse adminResponse = incidentAiService.getDiagnosticsSummary(testIncident.getId());
        assertNotNull(adminResponse);

        // 4. Admin user is read-only and cannot trigger diagnostics mutation
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> incidentAiService.requestOrRunDiagnostics(testIncident.getId(), true));

        // 5. Cross-tenant user cannot read or trigger diagnostics
        authenticateAs(otherUser);
        assertThrows(ResourceNotFoundException.class, () -> incidentAiService.getDiagnosticsSummary(testIncident.getId()));
        assertThrows(ResourceNotFoundException.class, () -> incidentAiService.requestOrRunDiagnostics(testIncident.getId(), true));
    }

    // --- 11. Python Offline Fallback Classified as FAILED ---

    @Test
    @DisplayName("Offline Fallback: Python fallback response is safely classified as FAILED")
    void testPythonOfflineFallback_ClassifiedAsFailed() {
        authenticateAs(ownerUser);

        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenReturn(Map.of(
                        "primary_root_cause", "Fallback Anomaly Indicator",
                        "failure_category", "GENERAL_ANOMALY",
                        "primary_evidence", "AI Service offline; fallback rule triggered"
                ));

        IncidentAiAnalysis result = incidentAiService.requestOrRunDiagnostics(testIncident.getId(), false);

        assertNotNull(result);
        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getFailureReason().contains("AI microservice is unavailable"));
        assertNotNull(result.getCompletedAt());
    }
}
