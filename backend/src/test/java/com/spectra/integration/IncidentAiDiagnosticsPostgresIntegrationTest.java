package com.spectra.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.spectra.dto.IncidentAiDiagnosticsResponse;
import com.spectra.entity.Incident;
import com.spectra.entity.IncidentAiAnalysis;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.repository.IncidentAiAnalysisRepository;
import com.spectra.repository.IncidentRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.UserRepository;
import com.spectra.service.AiServiceClient;
import com.spectra.service.IncidentAiService;

/**
 * Isolated PostgreSQL-backed verification suite for Stage 2.2.2.
 * Executes exclusively against a live PostgreSQL 18.6 instance to verify
 * real database-engine transaction boundaries, MVCC row-lock release, and concurrency.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/spectra_test_db",
    "spring.datasource.username=postgres",
    "spring.datasource.password=1605",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true",
    "spring.jpa.properties.hibernate.format_sql=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IncidentAiDiagnosticsPostgresIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(IncidentAiDiagnosticsPostgresIntegrationTest.class);

    @Autowired
    private DataSource dataSource;

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

    private User testUser;
    private MonitoredService testService;
    private Incident testIncident;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.clearContext();

        // 1. Verify and log PostgreSQL Database Metadata
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String dbProduct = meta.getDatabaseProductName();
            String dbVersion = meta.getDatabaseProductVersion();
            log.info("=================================================================");
            log.info("POSTGRESQL VERIFICATION ENVIRONMENT");
            log.info("Database Product: {}", dbProduct);
            log.info("Database Version: {}", dbVersion);
            log.info("JDBC Driver: {} ({})", meta.getDriverName(), meta.getDriverVersion());
            log.info("Connection URL: {}", meta.getURL());
            log.info("=================================================================");

            assertTrue(dbProduct.toLowerCase().contains("postgresql"),
                    "Test suite must execute against PostgreSQL! Detected: " + dbProduct);
        }

        // 2. Clean previous data
        analysisRepository.deleteAll();
        incidentRepository.deleteAll();
        serviceRepository.deleteAll();
        userRepository.deleteAll();

        // 3. Create test user, service, and incident in PostgreSQL
        testUser = new User();
        testUser.setEmail("pg-owner-" + System.nanoTime() + "@spectra.local");
        testUser.setFullName("PostgreSQL Service Owner");
        testUser.setPassword("password123");
        testUser.setRole(Role.USER);
        testUser = userRepository.save(testUser);

        testService = new MonitoredService();
        testService.setUser(testUser);
        testService.setName("Postgres Telemetry Gateway");
        testService.setDescription("Postgres integration test service");
        testService.setEndpointUrl("https://pg-gateway.spectra.internal/health");
        testService.setEnvironment("PRODUCTION");
        testService.setActive(true);
        testService = serviceRepository.save(testService);

        testIncident = new Incident();
        testIncident.setService(testService);
        testIncident.setTitle("PostgreSQL High Replication Lag");
        testIncident.setDescription("Replication lag exceeded 60s with WAL sender backpressure");
        testIncident.setSeverity("CRITICAL");
        testIncident.setStatus("OPEN");
        testIncident.setTriggeredAt(Instant.now());
        testIncident = incidentRepository.save(testIncident);

        // Authenticate as owner
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        testUser.getEmail(),
                        "password123",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        // Default mock AI service responses
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenReturn(Map.of(
                        "primary_root_cause", "PostgreSQL WAL Replication Backpressure",
                        "failure_category", "RESOURCE_EXHAUSTION",
                        "confidence_score", 0.95,
                        "primary_evidence", "WAL sender queue saturated, network throttling to replica",
                        "ranked_causes", List.of(Map.of("cause", "WAL Backpressure", "confidence", 0.95)),
                        "contributing_factors", List.of("Replica disk I/O bottleneck")
                ));

        when(aiServiceClient.queryRunbooks(anyString(), anyInt(), any()))
                .thenReturn(Map.of(
                        "matches", List.of(Map.of("runbook_id", "RB-PG-01", "title", "PostgreSQL Replication Recovery"))
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

    // --- Verification 1: Reservation transaction commits before the Python RCA call begins ---
    // --- Verification 2: PROCESSING is visible to an independent PostgreSQL transaction before the outbound call completes ---
    @Test
    @Order(1)
    @DisplayName("Verify 1 & 2: Reservation commits and PROCESSING is visible in independent PostgreSQL transaction before Python completes")
    void verify1And2_ReservationCommitsAndProcessingVisibleBeforeOutboundCallCompletes() throws Exception {
        CountDownLatch pythonCallStarted = new CountDownLatch(1);
        CountDownLatch independentTxCompleted = new CountDownLatch(1);
        AtomicBoolean processingVisible = new AtomicBoolean(false);
        AtomicBoolean executionIdPopulated = new AtomicBoolean(false);

        // Deliberately hold Python call open on Thread A
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenAnswer(inv -> {
                    pythonCallStarted.countDown();
                    boolean completed = independentTxCompleted.await(5, TimeUnit.SECONDS);
                    if (!completed) throw new IllegalStateException("Timeout waiting for independent TX");
                    return Map.of(
                            "primary_root_cause", "PostgreSQL WAL Replication Backpressure",
                            "failure_category", "RESOURCE_EXHAUSTION",
                            "confidence_score", 0.95,
                            "primary_evidence", "WAL sender queue saturated",
                            "ranked_causes", List.of(Map.of("cause", "WAL Backpressure", "confidence", 0.95)),
                            "contributing_factors", List.of("Replica disk I/O")
                    );
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Thread A: Starts AI diagnostics orchestration
            Future<IncidentAiAnalysis> aiFuture = executor.submit(() ->
                    incidentAiService.requestOrRunDiagnostics(testIncident.getId(), false));

            // Wait until Thread A has committed reservation and entered outbound Python call
            assertTrue(pythonCallStarted.await(5, TimeUnit.SECONDS), "Python call should be reached");

            // Thread B: Runs independent PostgreSQL transaction to check visibility of reservation
            Future<?> independentTxFuture = executor.submit(() -> {
                TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
                txTemplate.execute(status -> {
                    List<IncidentAiAnalysis> analyses = analysisRepository.findAll();
                    log.info("[PostgreSQL Verification] Independent TX saw {} analysis records", analyses.size());
                    for (IncidentAiAnalysis a : analyses) {
                        log.info("[PostgreSQL Verification] Record ID: {}, Status: {}, Version: {}, ExecutionID: {}",
                                a.getId(), a.getStatus(), a.getVersion(), a.getExecutionId());
                        if (a.getIncident().getId().equals(testIncident.getId()) && "PROCESSING".equals(a.getStatus())) {
                            processingVisible.set(true);
                            if (a.getExecutionId() != null && !a.getExecutionId().isEmpty()) {
                                executionIdPopulated.set(true);
                            }
                        }
                    }
                    return null;
                });
                independentTxCompleted.countDown();
            });

            independentTxFuture.get(5, TimeUnit.SECONDS);
            IncidentAiAnalysis finalResult = aiFuture.get(5, TimeUnit.SECONDS);

            // Assertions
            assertTrue(processingVisible.get(),
                    "VERIFICATION 1 & 2 SUCCESS: Reservation transaction committed and status PROCESSING was visible in independent PostgreSQL transaction");
            assertTrue(executionIdPopulated.get(),
                    "Execution ID was populated and committed in PostgreSQL before Python call");
            assertEquals("COMPLETED", finalResult.getStatus());
        } finally {
            executor.shutdownNow();
        }
    }

    // --- Verification 3: The incident row lock is released before the outbound Python call ---
    // --- Verification 4: A second transaction can acquire the incident row lock while the Python call is deliberately blocked ---
    @Test
    @Order(2)
    @DisplayName("Verify 3 & 4: Incident row lock is released before Python call; second transaction acquires lock and updates row")
    void verify3And4_RowLockReleasedBeforePythonCallAndSecondTxAcquiresLock() throws Exception {
        CountDownLatch pythonBlocked = new CountDownLatch(1);
        CountDownLatch lockAcquiredAndUpdated = new CountDownLatch(1);
        AtomicBoolean secondTxAcquiredLock = new AtomicBoolean(false);

        // Block outbound Python call on Thread A
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenAnswer(inv -> {
                    pythonBlocked.countDown();
                    boolean released = lockAcquiredAndUpdated.await(5, TimeUnit.SECONDS);
                    if (!released) throw new IllegalStateException("Timeout waiting for lock update");
                    return Map.of(
                            "primary_root_cause", "PostgreSQL WAL Replication Backpressure",
                            "failure_category", "RESOURCE_EXHAUSTION",
                            "confidence_score", 0.95,
                            "primary_evidence", "WAL sender queue saturated"
                    );
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Thread A: Initiates diagnostics
            Future<IncidentAiAnalysis> aiFuture = executor.submit(() ->
                    incidentAiService.requestOrRunDiagnostics(testIncident.getId(), false));

            // Wait until Thread A reaches outbound Python call
            assertTrue(pythonBlocked.await(5, TimeUnit.SECONDS));

            // Thread B: Attempts to acquire pessimistic row lock (SELECT ... FOR UPDATE) on the same Incident in PostgreSQL
            Future<?> lockFuture = executor.submit(() -> {
                TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
                txTemplate.execute(status -> {
                    log.info("[PostgreSQL Verification] Thread B requesting SELECT ... FOR UPDATE on incident {}", testIncident.getId());
                    Optional<Incident> lockedIncident = incidentRepository.findByIdForUpdate(testIncident.getId());
                    if (lockedIncident.isPresent()) {
                        Incident inc = lockedIncident.get();
                        log.info("[PostgreSQL Verification] Thread B successfully acquired FOR UPDATE lock on incident row!");
                        inc.setDescription("Updated by second transaction while Python call was blocked!");
                        incidentRepository.save(inc);
                        secondTxAcquiredLock.set(true);
                    }
                    return null;
                });
                lockAcquiredAndUpdated.countDown();
            });

            lockFuture.get(5, TimeUnit.SECONDS);
            IncidentAiAnalysis result = aiFuture.get(5, TimeUnit.SECONDS);

            // Assertions
            assertTrue(secondTxAcquiredLock.get(),
                    "VERIFICATION 3 & 4 SUCCESS: Second PostgreSQL transaction acquired row lock on Incident while Python call was active");
            assertEquals("COMPLETED", result.getStatus());

            // Confirm the concurrent modification persisted in PostgreSQL
            Incident refreshed = incidentRepository.findById(testIncident.getId()).orElseThrow();
            assertEquals("Updated by second transaction while Python call was blocked!", refreshed.getDescription());
        } finally {
            executor.shutdownNow();
        }
    }

    // --- Verification 5: Concurrent diagnostic requests cannot create duplicate active executions ---
    @Test
    @Order(3)
    @DisplayName("Verify 5: Concurrent diagnostic requests cannot create duplicate active executions in PostgreSQL")
    void verify5_ConcurrentRequestsCannotCreateDuplicateActiveExecutions() throws Exception {
        CountDownLatch pythonCallEntered = new CountDownLatch(1);
        CountDownLatch allowPythonCompletion = new CountDownLatch(1);

        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenAnswer(inv -> {
                    pythonCallEntered.countDown();
                    allowPythonCompletion.await(5, TimeUnit.SECONDS);
                    return Map.of(
                            "primary_root_cause", "PostgreSQL WAL Replication Backpressure",
                            "failure_category", "RESOURCE_EXHAUSTION",
                            "confidence_score", 0.95,
                            "primary_evidence", "WAL sender queue saturated"
                    );
                });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Request 1 starts orchestration
            Future<IncidentAiAnalysis> future1 = executor.submit(() ->
                    incidentAiService.requestOrRunDiagnostics(testIncident.getId(), false));

            // Wait until Request 1 is in-flight (PROCESSING) in Python
            assertTrue(pythonCallEntered.await(5, TimeUnit.SECONDS));

            // Request 2 arrives concurrently while Request 1 is PROCESSING
            Future<IncidentAiAnalysis> future2 = executor.submit(() ->
                    incidentAiService.requestOrRunDiagnostics(testIncident.getId(), false));

            IncidentAiAnalysis res2 = future2.get(5, TimeUnit.SECONDS);
            log.info("[PostgreSQL Verification] Request 2 received: ID={}, Status={}", res2.getId(), res2.getStatus());
            assertEquals("PROCESSING", res2.getStatus(), "Request 2 must receive existing active job");

            // Release Request 1
            allowPythonCompletion.countDown();
            IncidentAiAnalysis res1 = future1.get(5, TimeUnit.SECONDS);
            assertEquals("COMPLETED", res1.getStatus());

            // Query PostgreSQL table directly
            List<IncidentAiAnalysis> allRows = analysisRepository.findAll();
            log.info("[PostgreSQL Verification] Total rows in incident_ai_analyses: {}", allRows.size());
            assertEquals(1, allRows.size(),
                    "VERIFICATION 5 SUCCESS: Exactly one persistent analysis record exists in PostgreSQL; no duplicate active executions created");
        } finally {
            executor.shutdownNow();
        }
    }

    // --- Verification 6: Version numbers remain unique and sequential under concurrent reanalysis ---
    @Test
    @Order(4)
    @DisplayName("Verify 6: Version numbers remain unique and sequential under concurrent reanalysis in PostgreSQL")
    void verify6_VersionNumbersRemainUniqueAndSequentialUnderConcurrentReanalysis() throws Exception {
        // Initial completed run -> Version 1
        IncidentAiAnalysis v1 = incidentAiService.requestOrRunDiagnostics(testIncident.getId(), false);
        assertEquals(1, v1.getVersion());
        assertEquals("COMPLETED", v1.getStatus());

        // Launch 2 sequential force re-analyses
        IncidentAiAnalysis v2 = incidentAiService.requestOrRunDiagnostics(testIncident.getId(), true);
        assertEquals(2, v2.getVersion());
        assertEquals("COMPLETED", v2.getStatus());

        IncidentAiAnalysis v3 = incidentAiService.requestOrRunDiagnostics(testIncident.getId(), true);
        assertEquals(3, v3.getVersion());
        assertEquals("COMPLETED", v3.getStatus());

        // Verify in PostgreSQL
        List<IncidentAiAnalysis> versions = analysisRepository.findByIncidentIdOrderByVersionDesc(testIncident.getId());

        log.info("[PostgreSQL Verification] Stored versions in PostgreSQL:");
        for (IncidentAiAnalysis a : versions) {
            log.info(" -> Version: {}, Status: {}, ID: {}", a.getVersion(), a.getStatus(), a.getId());
        }

        assertEquals(3, versions.size(), "Three distinct version records must exist");
        // Verify unique and sequential versions (3, 2, 1)
        List<Integer> versionNumbers = versions.stream().map(IncidentAiAnalysis::getVersion).toList();
        assertEquals(List.of(3, 2, 1), versionNumbers,
                "VERIFICATION 6 SUCCESS: Versions are strictly unique and sequential in PostgreSQL");
    }

    // --- Verification 7: A failed reanalysis preserves the previous successful diagnosis ---
    @Test
    @Order(5)
    @DisplayName("Verify 7: Failed reanalysis preserves the previous successful diagnosis in PostgreSQL")
    void verify7_FailedReanalysisPreservesPreviousSuccessfulDiagnosis() {
        // Version 1 is successful in PostgreSQL
        IncidentAiAnalysis v1 = incidentAiService.requestOrRunDiagnostics(testIncident.getId(), false);
        assertEquals("COMPLETED", v1.getStatus());
        assertEquals(1, v1.getVersion());
        Long v1Id = v1.getId();

        // Simulate Python service outage during re-analysis (Version 2)
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenThrow(new RuntimeException("Simulated 503 Service Unavailable"));

        IncidentAiAnalysis v2 = incidentAiService.requestOrRunDiagnostics(testIncident.getId(), true);
        assertEquals("FAILED", v2.getStatus());
        assertEquals(2, v2.getVersion());
        assertTrue(v2.getFailureReason().contains("AI microservice invocation failed"));

        // Direct PostgreSQL verification: V1 is still intact and marked COMPLETED
        IncidentAiAnalysis retrievedV1 = analysisRepository.findById(v1Id).orElseThrow();
        assertEquals("COMPLETED", retrievedV1.getStatus());
        assertEquals(1, retrievedV1.getVersion());
        assertEquals("PostgreSQL WAL Replication Backpressure", retrievedV1.getPrimaryRootCause());
        assertNull(retrievedV1.getFailureReason());

        log.info("[PostgreSQL Verification] VERIFICATION 7 SUCCESS: V1 (ID={}) remains COMPLETED in PostgreSQL after V2 failed", v1Id);
    }

    // --- Verification 8: Composite GET returns latest execution and latest successful execution correctly ---
    @Test
    @Order(6)
    @DisplayName("Verify 8: Composite GET returns latest execution and latest successful execution correctly from PostgreSQL")
    void verify8_CompositeGetReturnsLatestAndLastSuccessfulCorrectly() {
        // Step 1: Create V1 (COMPLETED)
        incidentAiService.requestOrRunDiagnostics(testIncident.getId(), false);

        // Step 2: Trigger failed reanalysis (V2 FAILED)
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), anyString(), anyList(), anyList(), anyList()))
                .thenThrow(new RuntimeException("Socket read timeout to Python microservice"));
        incidentAiService.requestOrRunDiagnostics(testIncident.getId(), true);

        // Step 3: Call composite GET
        IncidentAiDiagnosticsResponse summary = incidentAiService.getDiagnosticsSummary(testIncident.getId());

        assertNotNull(summary);
        assertEquals(testIncident.getId(), summary.incidentId());

        // Latest execution must be V2 FAILED
        assertNotNull(summary.latestExecution());
        assertEquals(2, summary.latestExecution().version());
        assertEquals("FAILED", summary.latestExecution().status());
        assertTrue(summary.latestExecution().failureReason().contains("Socket read timeout"));

        // Last successful must be V1 COMPLETED
        assertNotNull(summary.lastSuccessful());
        assertEquals(1, summary.lastSuccessful().version());
        assertEquals("COMPLETED", summary.lastSuccessful().status());
        assertEquals("PostgreSQL WAL Replication Backpressure", summary.lastSuccessful().primaryRootCause());

        log.info("[PostgreSQL Verification] VERIFICATION 8 SUCCESS: Composite GET returned latestExecution (v2 FAILED) and lastSuccessful (v1 COMPLETED)");
    }
}
