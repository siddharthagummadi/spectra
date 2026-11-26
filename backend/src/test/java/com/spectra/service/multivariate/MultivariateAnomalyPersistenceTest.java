package com.spectra.service.multivariate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.spectra.entity.AnomalyEvent;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.ServiceMetric;
import com.spectra.repository.AnomalyEventRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.ServiceMetricRepository;

@SpringBootTest
@ActiveProfiles("test")
public class MultivariateAnomalyPersistenceTest {

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

    private MonitoredService service1;
    private MonitoredService service2;
    private MultivariateCalibrationModel dummyModel;

    private final List<Long> createdAnomalyIds = new ArrayList<>();
    private final List<Long> createdServiceIds = new ArrayList<>();
    private final List<Long> createdMetricIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service1 = new MonitoredService();
        service1.setName("Payment Gateway Test " + System.nanoTime());
        service1.setDescription("Test Service 1");
        service1.setEndpointUrl("http://localhost:8081");
        service1.setEnvironment("TEST");
        service1.setActive(true);
        service1 = serviceRepository.save(service1);
        createdServiceIds.add(service1.getId());

        service2 = new MonitoredService();
        service2.setName("Auth Service Test " + System.nanoTime());
        service2.setDescription("Test Service 2");
        service2.setEndpointUrl("http://localhost:8082");
        service2.setEnvironment("TEST");
        service2.setActive(true);
        service2 = serviceRepository.save(service2);
        createdServiceIds.add(service2.getId());

        dummyModel = new MultivariateCalibrationModel(
            service1.getId(),
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

    private MultivariateDetectionResult createAnomalyResult(double dm) {
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
    void firstMultivariateEvent_InsertionSucceeds() {
        Instant timestamp = Instant.parse("2026-11-12T12:00:00Z");
        MultivariateFeatureVector vector = MultivariateFeatureVector.of(90.0, 80.0, 500.0, 5.0, timestamp);
        MultivariateDetectionResult detection = createAnomalyResult(5.2);

        Optional<AnomalyEvent> result = persister.persistMultivariateAnomaly(service1, vector, detection, dummyModel);

        assertTrue(result.isPresent());
        AnomalyEvent event = result.get();
        createdAnomalyIds.add(event.getId());

        assertNotNull(event.getId());
        assertEquals("MULTIVARIATE_MAHALANOBIS", event.getMetricName());
        assertNull(event.getMetric()); // Invariant: metric_id is NULL for 4D events
        assertEquals(service1.getId(), event.getService().getId());
        assertEquals(timestamp, event.getDetectedAt());
        assertEquals(5.2, event.getObservedValue());
        assertEquals(MultivariateAnomalyEventPersister.BASELINE_MEAN_DM, event.getBaselineMean(), 0.001);
        assertEquals(MultivariateAnomalyEventPersister.BASELINE_STD_DEV_DM, event.getBaselineStdDev(), 0.001);
        assertEquals((5.2 - MultivariateAnomalyEventPersister.BASELINE_MEAN_DM) / MultivariateAnomalyEventPersister.BASELINE_STD_DEV_DM, event.getAnomalyScore(), 0.001);
        assertEquals("CRITICAL", event.getSeverity());
        assertEquals("POSITIVE_SPIKE", event.getDirection());
        assertTrue(event.getMessage().contains("[MULTIVARIATE_MAHALANOBIS]"));
    }

    @Test
    void duplicateLogicalEvent_SuppressedIdempotently() {
        Instant timestamp = Instant.parse("2026-11-12T12:00:00Z");
        MultivariateFeatureVector vector = MultivariateFeatureVector.of(90.0, 80.0, 500.0, 5.0, timestamp);
        MultivariateDetectionResult detection = createAnomalyResult(5.2);

        // 1. First insertion succeeds
        Optional<AnomalyEvent> first = persister.persistMultivariateAnomaly(service1, vector, detection, dummyModel);
        assertTrue(first.isPresent());
        createdAnomalyIds.add(first.get().getId());

        // 2. Exact second insertion with same (service_id, detector_type, detected_at)
        Optional<AnomalyEvent> second = persister.persistMultivariateAnomaly(service1, vector, detection, dummyModel);

        // Invariant: Second insert must be suppressed cleanly
        assertTrue(second.isEmpty());

        // Verify total records in database for this service and timestamp is exactly 1
        boolean exists = anomalyEventRepository.existsByServiceIdAndMetricNameAndDetectedAt(
            service1.getId(), "MULTIVARIATE_MAHALANOBIS", timestamp);
        assertTrue(exists);
    }

    @Test
    void concurrentDuplicateInsertion_ProducesExactlyOnePersistedRecord() throws Exception {
        Instant timestamp = Instant.parse("2026-11-12T12:00:00Z");
        MultivariateFeatureVector vector = MultivariateFeatureVector.of(90.0, 80.0, 500.0, 5.0, timestamp);
        MultivariateDetectionResult detection = createAnomalyResult(5.2);

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Optional<AnomalyEvent>> results = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Optional<AnomalyEvent> res = persister.persistMultivariateAnomaly(service1, vector, detection, dummyModel);
                    synchronized (results) {
                        results.add(res);
                    }
                } catch (Exception ex) {
                    // unexpected exception
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // Count how many succeeded
        long persistedCount = results.stream().filter(Optional::isPresent).count();
        assertEquals(1, persistedCount, "Exactly one concurrent insert must succeed");

        AnomalyEvent persisted = results.stream().filter(Optional::isPresent).findFirst().get().get();
        createdAnomalyIds.add(persisted.getId());

        // Database row count for this service must be strictly 1
        List<AnomalyEvent> events = anomalyEventRepository.findByServiceIdAndMetricNameOrderByDetectedAtDesc(
            service1.getId(), "MULTIVARIATE_MAHALANOBIS", org.springframework.data.domain.Pageable.unpaged());
        assertEquals(1, events.size());
    }

    @Test
    void independentServices_AndTimestamps_CreateDistinctEvents() {
        Instant t1 = Instant.parse("2026-11-12T12:00:00Z");
        Instant t2 = Instant.parse("2026-11-12T12:01:00Z");

        MultivariateFeatureVector v1 = MultivariateFeatureVector.of(90.0, 80.0, 500.0, 5.0, t1);
        MultivariateFeatureVector v2 = MultivariateFeatureVector.of(90.0, 80.0, 500.0, 5.0, t2);
        MultivariateDetectionResult detection = createAnomalyResult(4.5);

        // Service 1 at t1
        Optional<AnomalyEvent> e1 = persister.persistMultivariateAnomaly(service1, v1, detection, dummyModel);
        // Service 2 at t1 (different service, same timestamp)
        Optional<AnomalyEvent> e2 = persister.persistMultivariateAnomaly(service2, v1, detection, dummyModel);
        // Service 1 at t2 (same service, different timestamp)
        Optional<AnomalyEvent> e3 = persister.persistMultivariateAnomaly(service1, v2, detection, dummyModel);

        assertTrue(e1.isPresent());
        assertTrue(e2.isPresent());
        assertTrue(e3.isPresent());

        createdAnomalyIds.add(e1.get().getId());
        createdAnomalyIds.add(e2.get().getId());
        createdAnomalyIds.add(e3.get().getId());

        // All 3 IDs must be distinct
        assertFalse(e1.get().getId().equals(e2.get().getId()));
        assertFalse(e1.get().getId().equals(e3.get().getId()));
    }

    @Test
    void coexistenceWithUnivariate_UnivariateEventCreationUnchanged() {
        // Create scalar metric
        ServiceMetric metric = new ServiceMetric();
        metric.setService(service1);
        metric.setMetricName("CPU_USAGE");
        metric.setMetricValue(95.0);
        metric.setUnit("PERCENT");
        metric.setRecordedAt(Instant.now());
        metric = metricRepository.save(metric);
        createdMetricIds.add(metric.getId());

        // Persist univariate anomaly event (with metric_id)
        AnomalyEvent univariate = new AnomalyEvent();
        univariate.setService(service1);
        univariate.setMetric(metric); // has metric_id
        univariate.setMetricName("CPU_USAGE");
        univariate.setUnit("PERCENT");
        univariate.setObservedValue(95.0);
        univariate.setBaselineMean(50.0);
        univariate.setBaselineStdDev(2.0);
        univariate.setAnomalyScore(22.5);
        univariate.setSeverity("HIGH");
        univariate.setDirection("POSITIVE_SPIKE");
        univariate.setStatus("DETECTED");
        univariate.setMessage("High CPU spike");
        univariate.setDetectedAt(metric.getRecordedAt());
        univariate = anomalyEventRepository.save(univariate);
        createdAnomalyIds.add(univariate.getId());

        // Persist multivariate anomaly event for the same service
        MultivariateFeatureVector vector = MultivariateFeatureVector.of(95.0, 70.0, 300.0, 2.0, metric.getRecordedAt());
        MultivariateDetectionResult mvtDetection = createAnomalyResult(4.2);
        Optional<AnomalyEvent> multivariate = persister.persistMultivariateAnomaly(service1, vector, mvtDetection, dummyModel);

        assertTrue(multivariate.isPresent());
        createdAnomalyIds.add(multivariate.get().getId());

        assertNotNull(univariate.getMetric());
        assertNull(multivariate.get().getMetric());
    }

    @Test
    void foreignKeyViolation_PropagatesToCaller() {
        MonitoredService nonExistentService = new MonitoredService();
        nonExistentService.setId(9999999L); // ID does not exist in monitored_services table

        MultivariateFeatureVector vector = MultivariateFeatureVector.of(90.0, 80.0, 500.0, 5.0, Instant.now());
        MultivariateDetectionResult detection = createAnomalyResult(5.0);

        // Invariant: Unrelated database foreign key violation must propagate, NOT be swallowed as a duplicate!
        assertThrows(DataIntegrityViolationException.class, () -> {
            persister.persistMultivariateAnomaly(nonExistentService, vector, detection, dummyModel);
        });
    }

    @Test
    void callerTransaction_NoUnexpectedRollbackAfterSuppressedDuplicate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        Instant timestamp = Instant.parse("2026-11-12T12:00:00Z");
        MultivariateFeatureVector vector = MultivariateFeatureVector.of(90.0, 80.0, 500.0, 5.0, timestamp);
        MultivariateDetectionResult detection = createAnomalyResult(5.2);

        // Ingest first time outside transaction
        Optional<AnomalyEvent> first = persister.persistMultivariateAnomaly(service1, vector, detection, dummyModel);
        assertTrue(first.isPresent());
        createdAnomalyIds.add(first.get().getId());

        // Now run a caller transaction that triggers the duplicate suppression
        Boolean txCompleted = template.execute(status -> {
            // Second call inside caller transaction returns empty
            Optional<AnomalyEvent> duplicate = persister.persistMultivariateAnomaly(service1, vector, detection, dummyModel);
            assertTrue(duplicate.isEmpty());

            // Do another valid operation in the same transaction
            service1.setDescription("Updated in transaction after duplicate suppression");
            serviceRepository.save(service1);
            return true;
        });

        // Invariant: Must complete cleanly without UnexpectedRollbackException!
        assertTrue(txCompleted);
        assertEquals("Updated in transaction after duplicate suppression",
            serviceRepository.findById(service1.getId()).get().getDescription());
    }

    @Test
    void unrelatedUniqueConstraint_IsNotSuppressed() {
        // Construct a DataIntegrityViolationException simulating an unrelated unique constraint (e.g. uk_anomaly_events_metric_id)
        java.sql.SQLException sqlEx = new java.sql.SQLException(
            "ERROR: duplicate key value violates unique constraint \"uk_anomaly_events_metric_id\"",
            "23505",
            23505
        );
        DataIntegrityViolationException dive = new DataIntegrityViolationException("Unique constraint violation", sqlEx);

        // Invariant: Unrelated unique constraint must NOT be classified as a multivariate duplicate
        assertFalse(persister.isMultivariateDuplicateViolation(dive, service1.getId(), Instant.now()));
    }

    @Test
    void notNullConstraintViolation_IsNotSuppressed() {
        // Construct a DataIntegrityViolationException simulating a NOT NULL constraint violation (SQLState 23502)
        java.sql.SQLException sqlEx = new java.sql.SQLException(
            "ERROR: null value in column \"service_id\" violates not-null constraint",
            "23502",
            23502
        );
        DataIntegrityViolationException dive = new DataIntegrityViolationException("Not null violation", sqlEx);

        // Invariant: NOT NULL constraint violation must NOT be suppressed
        assertFalse(persister.isMultivariateDuplicateViolation(dive, service1.getId(), Instant.now()));
    }

    @Test
    void exactMultivariateDedupConstraint_IsRecognized() {
        java.sql.SQLException sqlEx = new java.sql.SQLException(
            "ERROR: duplicate key value violates unique constraint \"" + MultivariateAnomalyEventPersister.MULTIVARIATE_DEDUP_CONSTRAINT + "\"",
            "23505",
            23505
        );
        DataIntegrityViolationException dive = new DataIntegrityViolationException("Multivariate dedup violation", sqlEx);

        assertTrue(persister.isMultivariateDuplicateViolation(dive, service1.getId(), Instant.now()));
    }
}
