package com.spectra.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.spectra.entity.AnomalyEvent;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.ServiceMetric;
import com.spectra.repository.AnomalyEventRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.ServiceMetricRepository;
import com.spectra.service.multivariate.FeatureContribution;
import com.spectra.service.multivariate.MultivariateAnomalyEventPersister;
import com.spectra.service.multivariate.MultivariateCalibrationModel;
import com.spectra.service.multivariate.MultivariateDetectionResult;
import com.spectra.service.multivariate.MultivariateFeatureVector;
import com.spectra.service.multivariate.MultivariateMetricType;

/**
 * Isolated PostgreSQL-backed integration test suite executing exclusively against
 * a live PostgreSQL 18.6 instance (spectra_test_db).
 * <p>
 * Verifies real PostgreSQL catalog validation, partial unique index enforcement,
 * transaction rollback isolation with REQUIRES_NEW, and MVCC concurrent duplicate suppression.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/spectra_test_db",
    "spring.datasource.username=postgres",
    "spring.datasource.password=1605",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "spring.jpa.properties.hibernate.format_sql=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MultivariatePostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MultivariateAnomalyEventPersister persister;

    @Autowired
    private AnomalyEventRepository anomalyEventRepository;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    @Autowired
    private ServiceMetricRepository metricRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private MonitoredService pgService;
    private MultivariateCalibrationModel dummyModel;

    private final List<Long> createdAnomalyIds = new ArrayList<>();
    private final List<Long> createdServiceIds = new ArrayList<>();
    private final List<Long> createdMetricIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // 1. Apply the proposed PostgreSQL partial unique index on the isolated test database
        jdbcTemplate.execute(
            "CREATE UNIQUE INDEX IF NOT EXISTS uk_anomaly_events_multivariate_dedup " +
            "ON public.anomaly_events (service_id, detected_at) " +
            "WHERE (metric_id IS NULL AND metric_name = 'MULTIVARIATE_MAHALANOBIS')"
        );

        pgService = new MonitoredService();
        pgService.setName("PostgreSQL Test Service " + System.nanoTime());
        pgService.setDescription("Live Postgres 18.6 Test Service");
        pgService.setEndpointUrl("http://localhost:8080");
        pgService.setEnvironment("TEST");
        pgService.setActive(true);
        pgService = serviceRepository.save(pgService);
        createdServiceIds.add(pgService.getId());

        dummyModel = new MultivariateCalibrationModel(
            pgService.getId(),
            new double[] { 40.0, 50.0, 100.0, 0.2 },
            new double[] { 1.0, 1.0, 5.0, 0.1 },
            new double[][] {
                { 1.0, 0.0, 0.0, 0.0 },
                { 0.0, 1.0, 0.0, 0.0 },
                { 0.0, 0.0, 1.0, 0.0 },
                { 0.0, 0.0, 0.0, 1.0 }
            },
            new double[][] {
                { 1.0, 0.0, 0.0, 0.0 },
                { 0.0, 1.0, 0.0, 0.0 },
                { 0.0, 0.0, 1.0, 0.0 },
                { 0.0, 0.0, 0.0, 1.0 }
            },
            new double[][] {
                { 1.0, 0.0, 0.0, 0.0 },
                { 0.0, 1.0, 0.0, 0.0 },
                { 0.0, 0.0, 1.0, 0.0 },
                { 0.0, 0.0, 0.0, 1.0 }
            },
            0.05,
            1e-6,
            3.644,
            20,
            Instant.now()
        );
    }

    @AfterEach
    void tearDown() {
        for (Long id : createdAnomalyIds) {
            try { anomalyEventRepository.deleteById(id); } catch (Exception ignored) {}
        }
        for (Long id : createdMetricIds) {
            try { metricRepository.deleteById(id); } catch (Exception ignored) {}
        }
        for (Long id : createdServiceIds) {
            try { serviceRepository.deleteById(id); } catch (Exception ignored) {}
        }
    }

    private MultivariateDetectionResult createDetectionResult(double dm) {
        return MultivariateDetectionResult.anomaly(
            dm,
            dm * dm,
            3.644,
            List.of(
                new FeatureContribution(MultivariateMetricType.CPU_USAGE, 90.0, 40.0, 50.0, 25.0, 60.0, 1),
                new FeatureContribution(MultivariateMetricType.RESPONSE_TIME, 500.0, 100.0, 400.0, 15.0, 40.0, 2)
            ),
            MultivariateMetricType.CPU_USAGE,
            "CRITICAL",
            "Multivariate anomaly detected"
        );
    }

    @Test
    @Order(1)
    @DisplayName("PostgreSQL Catalog Validation: Table and Partial Unique Index Verification")
    void verifyPostgresIndexCatalogProperties() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            assertEquals("PostgreSQL", conn.getMetaData().getDatabaseProductName());
            assertTrue(conn.getMetaData().getDatabaseProductVersion().startsWith("18."));

            // Verify the index exists in pg_catalog and is attached to public.anomaly_events
            String sql =
                "SELECT c.relname AS index_name, i.indisunique, i.indisvalid, i.indisready " +
                "FROM pg_catalog.pg_class c " +
                "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace " +
                "JOIN pg_catalog.pg_index i ON i.indexrelid = c.oid " +
                "JOIN pg_catalog.pg_class t ON t.oid = i.indrelid " +
                "WHERE n.nspname = 'public' AND t.relname = 'anomaly_events' " +
                "  AND c.relname = 'uk_anomaly_events_multivariate_dedup'";

            try (ResultSet rs = conn.createStatement().executeQuery(sql)) {
                assertTrue(rs.next(), "Index uk_anomaly_events_multivariate_dedup must exist in pg_catalog");
                assertTrue(rs.getBoolean("indisunique"), "Must be a unique index");
                assertTrue(rs.getBoolean("indisvalid"), "Must be a valid index");
                assertTrue(rs.getBoolean("indisready"), "Must be ready for queries");
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("PostgreSQL Engine: First multivariate insert succeeds and commits cleanly")
    void postgres_FirstMultivariateEvent_InsertsSuccessfully() {
        Instant timestamp = Instant.parse("2026-11-12T15:30:00Z");
        MultivariateFeatureVector vector = MultivariateFeatureVector.of(88.0, 78.0, 480.0, 4.2, timestamp);
        MultivariateDetectionResult detection = createDetectionResult(5.8);

        Optional<AnomalyEvent> result = persister.persistMultivariateAnomaly(pgService, vector, detection, dummyModel);

        assertTrue(result.isPresent());
        AnomalyEvent saved = result.get();
        createdAnomalyIds.add(saved.getId());

        assertNotNull(saved.getId());
        assertEquals("MULTIVARIATE_MAHALANOBIS", saved.getMetricName());
        assertNull(saved.getMetric());
        assertEquals(pgService.getId(), saved.getService().getId());
        assertEquals(5.8, saved.getObservedValue());
        assertEquals(MultivariateAnomalyEventPersister.BASELINE_MEAN_DM, saved.getBaselineMean(), 0.001);
        assertEquals(MultivariateAnomalyEventPersister.BASELINE_STD_DEV_DM, saved.getBaselineStdDev(), 0.001);
        assertEquals("POSITIVE_SPIKE", saved.getDirection());
    }

    @Test
    @Order(3)
    @DisplayName("PostgreSQL Engine: Real database unique index violation caught and suppressed cleanly")
    void postgres_DuplicateEvent_ViolatesPartialIndexAndSuppressesCleanly() {
        Instant timestamp = Instant.parse("2026-11-12T15:40:00Z");
        MultivariateFeatureVector vector = MultivariateFeatureVector.of(88.0, 78.0, 480.0, 4.2, timestamp);
        MultivariateDetectionResult detection = createDetectionResult(5.8);

        // 1. First insert
        Optional<AnomalyEvent> first = persister.persistMultivariateAnomaly(pgService, vector, detection, dummyModel);
        assertTrue(first.isPresent());
        createdAnomalyIds.add(first.get().getId());

        // 2. Second insert with exact same service and timestamp
        Optional<AnomalyEvent> second = persister.persistMultivariateAnomaly(pgService, vector, detection, dummyModel);

        // Must return empty without throwing
        assertTrue(second.isEmpty());

        // Query database directly to confirm strictly 1 row in PostgreSQL
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.anomaly_events WHERE service_id = ? AND metric_name = 'MULTIVARIATE_MAHALANOBIS' AND detected_at = ?",
            Integer.class, pgService.getId(), java.sql.Timestamp.from(timestamp)
        );
        assertEquals(1, count);
    }

    @Test
    @Order(4)
    @DisplayName("PostgreSQL Engine: Concurrent threads on separate connections produce exactly one persisted record")
    void postgres_ConcurrentDuplicateInserts_ExactlyOnePersists() throws Exception {
        Instant timestamp = Instant.parse("2026-11-12T15:50:00Z");
        MultivariateFeatureVector vector = MultivariateFeatureVector.of(92.0, 85.0, 600.0, 8.0, timestamp);
        MultivariateDetectionResult detection = createDetectionResult(6.4);

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Optional<AnomalyEvent>> results = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Optional<AnomalyEvent> res = persister.persistMultivariateAnomaly(pgService, vector, detection, dummyModel);
                    synchronized (results) {
                        results.add(res);
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        long persisted = results.stream().filter(Optional::isPresent).count();
        assertEquals(1, persisted, "Exactly 1 concurrent thread must succeed in persisting");

        AnomalyEvent event = results.stream().filter(Optional::isPresent).findFirst().get().get();
        createdAnomalyIds.add(event.getId());

        Integer rowCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.anomaly_events WHERE service_id = ? AND detected_at = ?",
            Integer.class, pgService.getId(), java.sql.Timestamp.from(timestamp)
        );
        assertEquals(1, rowCount);
    }

    @Test
    @Order(5)
    @DisplayName("PostgreSQL Engine: Unrelated Foreign Key Violation Propagates Directly to Caller")
    void postgres_ForeignKeyViolation_PropagatesNormally() {
        MonitoredService missingService = new MonitoredService();
        missingService.setId(8888888L); // Non-existent service in Postgres

        MultivariateFeatureVector vector = MultivariateFeatureVector.of(88.0, 78.0, 480.0, 4.2, Instant.now());
        MultivariateDetectionResult detection = createDetectionResult(5.8);

        // PostgreSQL raises SQLSTATE 23503 (foreign_key_violation)
        assertThrows(DataIntegrityViolationException.class, () -> {
            persister.persistMultivariateAnomaly(missingService, vector, detection, dummyModel);
        });
    }

    @Test
    @Order(6)
    @DisplayName("PostgreSQL Engine: Caller transaction experiences NO UnexpectedRollbackException after duplicate suppression")
    void postgres_CallerTransaction_CleanAfterDuplicateSuppression() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        Instant timestamp = Instant.parse("2026-11-12T16:00:00Z");
        MultivariateFeatureVector vector = MultivariateFeatureVector.of(90.0, 80.0, 500.0, 5.0, timestamp);
        MultivariateDetectionResult detection = createDetectionResult(5.0);

        // First insert
        Optional<AnomalyEvent> first = persister.persistMultivariateAnomaly(pgService, vector, detection, dummyModel);
        assertTrue(first.isPresent());
        createdAnomalyIds.add(first.get().getId());

        // Run caller transaction in PostgreSQL
        Boolean success = txTemplate.execute(status -> {
            Optional<AnomalyEvent> dup = persister.persistMultivariateAnomaly(pgService, vector, detection, dummyModel);
            assertTrue(dup.isEmpty(), "Duplicate must return empty");

            // Perform additional update in same transaction
            pgService.setDescription("Updated description in live Postgres transaction");
            serviceRepository.save(pgService);
            return true;
        });

        assertTrue(success);
        assertEquals("Updated description in live Postgres transaction",
            serviceRepository.findById(pgService.getId()).get().getDescription());
    }

    @Test
    @Order(7)
    @DisplayName("PostgreSQL Engine: Coexistence with univariate anomaly events on PostgreSQL")
    void postgres_CoexistenceWithUnivariateEvents() {
        // 1. Create and save scalar metric in Postgres
        ServiceMetric m = new ServiceMetric();
        m.setService(pgService);
        m.setMetricName("RESPONSE_TIME");
        m.setMetricValue(2500.0);
        m.setUnit("MILLISECONDS");
        m.setRecordedAt(Instant.now());
        m = metricRepository.save(m);
        createdMetricIds.add(m.getId());

        // 2. Insert univariate anomaly event with metric_id
        AnomalyEvent uni = new AnomalyEvent();
        uni.setService(pgService);
        uni.setMetric(m);
        uni.setMetricName("RESPONSE_TIME");
        uni.setUnit("MILLISECONDS");
        uni.setObservedValue(2500.0);
        uni.setBaselineMean(200.0);
        uni.setBaselineStdDev(25.0);
        uni.setAnomalyScore(92.0);
        uni.setSeverity("CRITICAL");
        uni.setDirection("POSITIVE_SPIKE");
        uni.setStatus("DETECTED");
        uni.setMessage("Response time spike");
        uni.setDetectedAt(m.getRecordedAt());
        uni = anomalyEventRepository.save(uni);
        createdAnomalyIds.add(uni.getId());

        // 3. Insert multivariate anomaly event with metric_id = NULL
        MultivariateFeatureVector vector = MultivariateFeatureVector.of(50.0, 60.0, 2500.0, 1.0, m.getRecordedAt());
        MultivariateDetectionResult detection = createDetectionResult(5.5);
        Optional<AnomalyEvent> mvt = persister.persistMultivariateAnomaly(pgService, vector, detection, dummyModel);
        assertTrue(mvt.isPresent());
        createdAnomalyIds.add(mvt.get().getId());

        // Both coexist cleanly in PostgreSQL
        assertNotNull(uni.getMetric());
        assertNull(mvt.get().getMetric());
    }

    @Test
    @Order(8)
    @DisplayName("PostgreSQL Engine: Unrelated unique constraint (uk_anomaly_events_metric_id) violation propagates and is NOT suppressed")
    void postgres_UnrelatedUniqueConstraint_ThrowsAndIsNotSuppressed() {
        // Create a single metric
        ServiceMetric m = new ServiceMetric();
        m.setService(pgService);
        m.setMetricName("CPU_USAGE");
        m.setMetricValue(99.0);
        m.setUnit("PERCENT");
        m.setRecordedAt(Instant.now());
        m = metricRepository.save(m);
        createdMetricIds.add(m.getId());

        // First univariate event on this metric succeeds
        AnomalyEvent e1 = new AnomalyEvent();
        e1.setService(pgService);
        e1.setMetric(m);
        e1.setMetricName("CPU_USAGE");
        e1.setUnit("PERCENT");
        e1.setObservedValue(99.0);
        e1.setBaselineMean(50.0);
        e1.setBaselineStdDev(5.0);
        e1.setAnomalyScore(9.8);
        e1.setSeverity("HIGH");
        e1.setDirection("POSITIVE_SPIKE");
        e1.setStatus("DETECTED");
        e1.setMessage("CPU spike");
        e1.setDetectedAt(Instant.now());
        e1 = anomalyEventRepository.saveAndFlush(e1);
        createdAnomalyIds.add(e1.getId());

        // Second univariate event referencing the EXACT same metric violates uk_anomaly_events_metric_id
        AnomalyEvent e2 = new AnomalyEvent();
        e2.setService(pgService);
        e2.setMetric(m);
        e2.setMetricName("CPU_USAGE");
        e2.setUnit("PERCENT");
        e2.setObservedValue(99.5);
        e2.setBaselineMean(50.0);
        e2.setBaselineStdDev(5.0);
        e2.setAnomalyScore(9.9);
        e2.setSeverity("HIGH");
        e2.setDirection("POSITIVE_SPIKE");
        e2.setStatus("DETECTED");
        e2.setMessage("Duplicate CPU spike on same metric");
        e2.setDetectedAt(Instant.now());

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class, () -> {
            anomalyEventRepository.saveAndFlush(e2);
        });

        // Invariant: An unrelated unique constraint violation must NOT match multivariate deduplication
        assertFalse(persister.isMultivariateDuplicateViolation(thrown, pgService.getId(), Instant.now()));
    }

    @Test
    @Order(9)
    @DisplayName("PostgreSQL Engine: NOT NULL constraint violation propagates directly and is NOT suppressed")
    void postgres_NotNullConstraintViolation_Propagates() {
        // Attempt to persist an AnomalyEvent with missing required non-null fields
        AnomalyEvent invalidEvent = new AnomalyEvent();
        invalidEvent.setService(pgService);
        // Leaving metricName, observedValue, etc. null
        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class, () -> {
            anomalyEventRepository.saveAndFlush(invalidEvent);
        });

        assertFalse(persister.isMultivariateDuplicateViolation(thrown, pgService.getId(), Instant.now()));
    }
}
