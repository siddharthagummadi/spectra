package com.spectra.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.spectra.dto.CreateMetricRequest;
import com.spectra.entity.AlertEvent;
import com.spectra.entity.AlertRule;
import com.spectra.entity.AnomalyEvent;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.ServiceMetric;
import com.spectra.entity.User;
import com.spectra.repository.AlertEventRepository;
import com.spectra.repository.AlertRuleRepository;
import com.spectra.repository.AnomalyEventRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.ServiceMetricRepository;
import com.spectra.repository.UserRepository;
import com.spectra.service.AnomalyDetectionService;
import com.spectra.service.ServiceMetricService;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@ActiveProfiles("test")
class AnomalyDetectionIntegrationTest {

    private static final String OWNER_EMAIL = "anom_owner@spectra.local";
    private static final String OTHER_USER_EMAIL = "anom_other@spectra.local";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ServiceMetricService metricService;

    @Autowired
    private ServiceMetricRepository metricRepository;

    @Autowired
    private AnomalyEventRepository anomalyEventRepository;

    @Autowired
    private AnomalyDetectionService anomalyDetectionService;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private AlertEventRepository alertEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    private User ownerUser;
    private User otherUser;
    private MonitoredService testService;

    private final List<Long> testCreatedAnomalyIds = new ArrayList<>();
    private final List<Long> testCreatedMetricIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        ownerUser = userRepository.findByEmail(OWNER_EMAIL).orElseGet(() -> {
            User u = new User();
            u.setEmail(OWNER_EMAIL);
            u.setFullName("Anomaly Test Owner");
            u.setPassword(passwordEncoder.encode("Password123!"));
            u.setRole(Role.USER);
            return userRepository.save(u);
        });

        otherUser = userRepository.findByEmail(OTHER_USER_EMAIL).orElseGet(() -> {
            User u = new User();
            u.setEmail(OTHER_USER_EMAIL);
            u.setFullName("Anomaly Other User");
            u.setPassword(passwordEncoder.encode("Password123!"));
            u.setRole(Role.USER);
            return userRepository.save(u);
        });

        testService = new MonitoredService();
        testService.setName("Anomaly Integration Service");
        testService.setEndpointUrl("https://anom.spectra.local");
        testService.setEnvironment("PRODUCTION");
        testService.setActive(true);
        testService.setUser(ownerUser);
        testService = serviceRepository.save(testService);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("ALTER TABLE anomaly_events DROP CONSTRAINT IF EXISTS check_anomaly_fail");

        for (Long anomalyId : testCreatedAnomalyIds) {
            if (anomalyEventRepository.existsById(anomalyId)) {
                anomalyEventRepository.deleteById(anomalyId);
            }
        }
        testCreatedAnomalyIds.clear();

        for (Long metricId : testCreatedMetricIds) {
            if (metricRepository.existsById(metricId)) {
                metricRepository.deleteById(metricId);
            }
        }
        testCreatedMetricIds.clear();

        if (testService != null && testService.getId() != null) {
            // Also clean any anomalies created automatically by service
            anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
                    testService.getId(), org.springframework.data.domain.Pageable.unpaged())
                    .forEach(a -> anomalyEventRepository.deleteById(a.getId()));

            alertEventRepository.findByServiceIdOrderByTriggeredAtDescIdDesc(testService.getId())
                    .forEach(e -> alertEventRepository.deleteById(e.getId()));

            alertRuleRepository.findByServiceId(testService.getId())
                    .forEach(r -> alertRuleRepository.deleteById(r.getId()));

            metricRepository.findByServiceIdOrderByRecordedAtDescIdDesc(
                    testService.getId(), org.springframework.data.domain.Pageable.unpaged())
                    .forEach(m -> metricRepository.deleteById(m.getId()));

            serviceRepository.deleteById(testService.getId());
            testService = null;
        }

        if (ownerUser != null) {
            userRepository.deleteById(ownerUser.getId());
            ownerUser = null;
        }
        if (otherUser != null) {
            userRepository.deleteById(otherUser.getId());
            otherUser = null;
        }
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = {"USER"})
    void metricIngestion_WhenBaselineInsufficient_NoAnomalyCreated() {
        // Submit 9 normal metrics
        Instant baseTime = Instant.now().minusSeconds(1000);
        for (int i = 0; i < 9; i++) {
            CreateMetricRequest req = new CreateMetricRequest();
            req.setServiceId(testService.getId());
            req.setMetricName("CPU_USAGE");
            req.setMetricValue(50.0);
            req.setUnit("PERCENT");
            req.setRecordedAt(baseTime.plusSeconds(i * 10));

            ServiceMetric saved = metricService.submitMetric(req);
            testCreatedMetricIds.add(saved.getId());
        }

        List<AnomalyEvent> anomalies = anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
                testService.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertTrue(anomalies.isEmpty(), "No anomalies should be created when baseline has fewer than 10 observations");
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = {"USER"})
    void metricIngestion_WhenSpikeExceedsZ3_AnomalyAutomaticallyCreatedAndAccessible() throws Exception {
        // Step 1: Submit 10 baseline metrics at 50% CPU
        Instant baseTime = Instant.now().minusSeconds(1000);
        for (int i = 0; i < 10; i++) {
            CreateMetricRequest req = new CreateMetricRequest();
            req.setServiceId(testService.getId());
            req.setMetricName("CPU_USAGE");
            req.setMetricValue(50.0);
            req.setUnit("PERCENT");
            req.setRecordedAt(baseTime.plusSeconds(i * 10));

            ServiceMetric saved = metricService.submitMetric(req);
            testCreatedMetricIds.add(saved.getId());
        }

        // Verify baseline alone triggered 0 anomalies
        List<AnomalyEvent> beforeSpike = anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
                testService.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertTrue(beforeSpike.isEmpty());

        // Step 2: Ingest an 11th metric with CPU = 95.0% (Positive Spike)
        CreateMetricRequest spikeReq = new CreateMetricRequest();
        spikeReq.setServiceId(testService.getId());
        spikeReq.setMetricName("CPU_USAGE");
        spikeReq.setMetricValue(95.0);
        spikeReq.setUnit("PERCENT");
        spikeReq.setRecordedAt(baseTime.plusSeconds(120));

        ServiceMetric spikeMetric = metricService.submitMetric(spikeReq);
        testCreatedMetricIds.add(spikeMetric.getId());

        // Step 3: Verify anomaly was detected and persisted
        List<AnomalyEvent> anomalies = anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
                testService.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertEquals(1, anomalies.size());

        AnomalyEvent anomaly = anomalies.get(0);
        testCreatedAnomalyIds.add(anomaly.getId());

        assertEquals("CPU_USAGE", anomaly.getMetricName());
        assertEquals(95.0, anomaly.getObservedValue());
        assertEquals(50.0, anomaly.getBaselineMean(), 0.01);
        assertEquals(1.0, anomaly.getBaselineStdDev(), 0.01); // variance floor for percent is 1.0
        assertTrue(anomaly.getAnomalyScore() >= 5.0); // (95 - 50) / 1.0 = 45.0
        assertEquals("CRITICAL", anomaly.getSeverity());
        assertEquals("POSITIVE_SPIKE", anomaly.getDirection());
        assertEquals("DETECTED", anomaly.getStatus());

        // Step 4: Verify retrieval via REST API by owner
        mockMvc.perform(get("/api/anomalies/service/" + testService.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$[0].direction").value("POSITIVE_SPIKE"));

        // Step 5: Verify acknowledgement
        mockMvc.perform(patch("/api/anomalies/" + anomaly.getId() + "/acknowledge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedBy").value(OWNER_EMAIL));

        AnomalyEvent reloaded = anomalyEventRepository.findById(anomaly.getId()).orElseThrow();
        assertEquals("ACKNOWLEDGED", reloaded.getStatus());
        assertNotNull(reloaded.getAcknowledgedAt());
    }

    @Test
    @WithMockUser(username = OTHER_USER_EMAIL, roles = {"USER"})
    void workspaceIsolation_OtherUserCannotAccessOrAcknowledgeAnomalies() throws Exception {
        // Create an anomaly directly for owner's service
        AnomalyEvent event = new AnomalyEvent();
        event.setService(testService);
        event.setMetricName("RESPONSE_TIME");
        event.setUnit("MILLISECONDS");
        event.setObservedValue(500.0);
        event.setBaselineMean(100.0);
        event.setBaselineStdDev(20.0);
        event.setAnomalyScore(20.0);
        event.setSeverity("CRITICAL");
        event.setDirection("POSITIVE_SPIKE");
        event.setStatus("DETECTED");
        event.setMessage("High latency anomaly");
        event.setDetectedAt(Instant.now());
        event = anomalyEventRepository.save(event);
        testCreatedAnomalyIds.add(event.getId());

        // Other user attempts GET by ID -> 404
        mockMvc.perform(get("/api/anomalies/" + event.getId()))
                .andExpect(status().isNotFound());

        // Other user attempts GET by service ID -> 404
        mockMvc.perform(get("/api/anomalies/service/" + testService.getId()))
                .andExpect(status().isNotFound());

        // Other user attempts PATCH acknowledge -> 404
        mockMvc.perform(patch("/api/anomalies/" + event.getId() + "/acknowledge"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = {"USER"})
    void metricIngestion_ResponseTimeWithFloor_DetectsAnomaly() {
        // Step 1: Submit 10 baseline metrics of 100.0 ms
        Instant baseTime = Instant.now().minusSeconds(1000);
        for (int i = 0; i < 10; i++) {
            CreateMetricRequest req = new CreateMetricRequest();
            req.setServiceId(testService.getId());
            req.setMetricName("RESPONSE_TIME");
            req.setMetricValue(100.0);
            req.setUnit("MILLISECONDS");
            req.setRecordedAt(baseTime.plusSeconds(i * 10));

            ServiceMetric saved = metricService.submitMetric(req);
            testCreatedMetricIds.add(saved.getId());
        }

        // Step 2: Submit 102.0 ms -> delta is 2ms, below 5ms floor -> no anomaly
        CreateMetricRequest normalReq = new CreateMetricRequest();
        normalReq.setServiceId(testService.getId());
        normalReq.setMetricName("RESPONSE_TIME");
        normalReq.setMetricValue(102.0);
        normalReq.setUnit("MILLISECONDS");
        normalReq.setRecordedAt(baseTime.plusSeconds(105));

        ServiceMetric normalMetric = metricService.submitMetric(normalReq);
        testCreatedMetricIds.add(normalMetric.getId());

        List<AnomalyEvent> anomaliesAfterNormal = anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
                testService.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertTrue(anomaliesAfterNormal.isEmpty(), "Small variation within 5ms floor should not trigger anomaly");

        // Step 3: Submit 125.0 ms -> delta is ~25ms -> Z ≈ 4.96 -> HIGH severity anomaly
        CreateMetricRequest spikeReq = new CreateMetricRequest();
        spikeReq.setServiceId(testService.getId());
        spikeReq.setMetricName("RESPONSE_TIME");
        spikeReq.setMetricValue(125.0);
        spikeReq.setUnit("MILLISECONDS");
        spikeReq.setRecordedAt(baseTime.plusSeconds(120));

        ServiceMetric spikeMetric = metricService.submitMetric(spikeReq);
        testCreatedMetricIds.add(spikeMetric.getId());

        List<AnomalyEvent> anomaliesAfterSpike = anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
                testService.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertEquals(1, anomaliesAfterSpike.size());

        AnomalyEvent event = anomaliesAfterSpike.get(0);
        testCreatedAnomalyIds.add(event.getId());

        assertEquals("RESPONSE_TIME", event.getMetricName());
        assertEquals("MILLISECONDS", event.getUnit());
        assertEquals(125.0, event.getObservedValue());
        assertEquals(5.0, event.getBaselineStdDev(), 0.01);
        assertEquals("HIGH", event.getSeverity());
        assertEquals("POSITIVE_SPIKE", event.getDirection());
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = {"USER"})
    void metricIngestion_NegativeDrop_CorrectlyClassifiedAndPersisted() {
        // Step 1: Submit 10 baseline metrics at 80.0% CPU
        Instant baseTime = Instant.now().minusSeconds(1000);
        for (int i = 0; i < 10; i++) {
            CreateMetricRequest req = new CreateMetricRequest();
            req.setServiceId(testService.getId());
            req.setMetricName("CPU_USAGE");
            req.setMetricValue(80.0);
            req.setUnit("PERCENT");
            req.setRecordedAt(baseTime.plusSeconds(i * 10));

            ServiceMetric saved = metricService.submitMetric(req);
            testCreatedMetricIds.add(saved.getId());
        }

        // Step 2: Submit a drop to 10.0% CPU (e.g. abrupt loss of workload)
        CreateMetricRequest dropReq = new CreateMetricRequest();
        dropReq.setServiceId(testService.getId());
        dropReq.setMetricName("CPU_USAGE");
        dropReq.setMetricValue(10.0);
        dropReq.setUnit("PERCENT");
        dropReq.setRecordedAt(baseTime.plusSeconds(120));

        ServiceMetric dropMetric = metricService.submitMetric(dropReq);
        testCreatedMetricIds.add(dropMetric.getId());

        List<AnomalyEvent> anomalies = anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
                testService.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertEquals(1, anomalies.size());

        AnomalyEvent event = anomalies.get(0);
        testCreatedAnomalyIds.add(event.getId());

        assertEquals("CPU_USAGE", event.getMetricName());
        assertEquals(10.0, event.getObservedValue());
        assertEquals(80.0, event.getBaselineMean(), 0.01);
        assertTrue(event.getAnomalyScore() <= -5.0, "Score should be strongly negative");
        assertEquals("CRITICAL", event.getSeverity());
        assertEquals("NEGATIVE_DROP", event.getDirection());
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = {"USER"})
    void metricIngestion_MemoryUsage_IsolatesPercentAndMegabytesBaselines() {
        Instant baseTime = Instant.now().minusSeconds(2000);

        // Step 1: Submit 10 MEMORY_USAGE metrics in MEGABYTES (value 500.0 MB)
        for (int i = 0; i < 10; i++) {
            CreateMetricRequest req = new CreateMetricRequest();
            req.setServiceId(testService.getId());
            req.setMetricName("MEMORY_USAGE");
            req.setMetricValue(500.0);
            req.setUnit("MEGABYTES");
            req.setRecordedAt(baseTime.plusSeconds(i * 10));

            ServiceMetric saved = metricService.submitMetric(req);
            testCreatedMetricIds.add(saved.getId());
        }

        // Step 2: Submit 1 MEMORY_USAGE metric in PERCENT (value 50.0%)
        // If baseline unit isolation works, the PERCENT query finds 0 history (not the 10 MEGABYTES records),
        // so no anomaly is triggered.
        CreateMetricRequest firstPercentReq = new CreateMetricRequest();
        firstPercentReq.setServiceId(testService.getId());
        firstPercentReq.setMetricName("MEMORY_USAGE");
        firstPercentReq.setMetricValue(50.0);
        firstPercentReq.setUnit("PERCENT");
        firstPercentReq.setRecordedAt(baseTime.plusSeconds(110));

        ServiceMetric firstPercentMetric = metricService.submitMetric(firstPercentReq);
        testCreatedMetricIds.add(firstPercentMetric.getId());

        List<AnomalyEvent> anomaliesAfterFirstPercent = anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
                testService.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertTrue(anomaliesAfterFirstPercent.isEmpty(),
                "PERCENT metric must not evaluate against MEGABYTES history and trigger false anomaly");

        // Step 3: Submit 9 more MEMORY_USAGE metrics in PERCENT (total 10 PERCENT records)
        for (int i = 1; i < 10; i++) {
            CreateMetricRequest req = new CreateMetricRequest();
            req.setServiceId(testService.getId());
            req.setMetricName("MEMORY_USAGE");
            req.setMetricValue(50.0);
            req.setUnit("PERCENT");
            req.setRecordedAt(baseTime.plusSeconds(110 + i * 10));

            ServiceMetric saved = metricService.submitMetric(req);
            testCreatedMetricIds.add(saved.getId());
        }

        // Step 4: Ingest 11th PERCENT metric with spike = 95.0%
        // Baseline must be the 10 PERCENT records (mean = 50.0%, floor = 1.0%), not MEGABYTES (500.0)
        CreateMetricRequest percentSpikeReq = new CreateMetricRequest();
        percentSpikeReq.setServiceId(testService.getId());
        percentSpikeReq.setMetricName("MEMORY_USAGE");
        percentSpikeReq.setMetricValue(95.0);
        percentSpikeReq.setUnit("PERCENT");
        percentSpikeReq.setRecordedAt(baseTime.plusSeconds(250));

        ServiceMetric percentSpike = metricService.submitMetric(percentSpikeReq);
        testCreatedMetricIds.add(percentSpike.getId());

        List<AnomalyEvent> anomaliesAfterPercentSpike = anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
                testService.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertEquals(1, anomaliesAfterPercentSpike.size());
        AnomalyEvent percentAnomaly = anomaliesAfterPercentSpike.get(0);
        testCreatedAnomalyIds.add(percentAnomaly.getId());

        assertEquals("PERCENT", percentAnomaly.getUnit());
        assertEquals(95.0, percentAnomaly.getObservedValue());
        assertEquals(50.0, percentAnomaly.getBaselineMean(), 0.01);
        assertEquals(1.0, percentAnomaly.getBaselineStdDev(), 0.01);
        assertEquals("CRITICAL", percentAnomaly.getSeverity());

        // Step 5: Ingest 11th MEGABYTES metric with spike = 540.0 MB
        // Baseline must be the 10 MEGABYTES records (mean = 500.0 MB, floor = 10.0 MB), ignoring PERCENT records
        CreateMetricRequest mbSpikeReq = new CreateMetricRequest();
        mbSpikeReq.setServiceId(testService.getId());
        mbSpikeReq.setMetricName("MEMORY_USAGE");
        mbSpikeReq.setMetricValue(540.0);
        mbSpikeReq.setUnit("MEGABYTES");
        mbSpikeReq.setRecordedAt(baseTime.plusSeconds(260));

        ServiceMetric mbSpike = metricService.submitMetric(mbSpikeReq);
        testCreatedMetricIds.add(mbSpike.getId());

        List<AnomalyEvent> allAnomalies = anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
                testService.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertEquals(2, allAnomalies.size());
        AnomalyEvent mbAnomaly = allAnomalies.get(0); // newest first
        testCreatedAnomalyIds.add(mbAnomaly.getId());

        assertEquals("MEGABYTES", mbAnomaly.getUnit());
        assertEquals(540.0, mbAnomaly.getObservedValue());
        assertEquals(500.0, mbAnomaly.getBaselineMean(), 0.01);
        assertEquals(10.0, mbAnomaly.getBaselineStdDev(), 0.01);
        assertEquals(4.0, mbAnomaly.getAnomalyScore(), 0.01);
        assertEquals("HIGH", mbAnomaly.getSeverity());
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = {"USER"})
    void metricEvaluation_DuplicateEvaluation_IsIdempotentAndGuaranteedByUniqueConstraint() {
        // Step 1: Submit 10 baseline metrics at 50% CPU
        Instant baseTime = Instant.now().minusSeconds(1000);
        for (int i = 0; i < 10; i++) {
            CreateMetricRequest req = new CreateMetricRequest();
            req.setServiceId(testService.getId());
            req.setMetricName("CPU_USAGE");
            req.setMetricValue(50.0);
            req.setUnit("PERCENT");
            req.setRecordedAt(baseTime.plusSeconds(i * 10));

            ServiceMetric saved = metricService.submitMetric(req);
            testCreatedMetricIds.add(saved.getId());
        }

        // Step 2: Ingest 11th metric at 95% CPU -> automatically generates 1 AnomalyEvent
        CreateMetricRequest spikeReq = new CreateMetricRequest();
        spikeReq.setServiceId(testService.getId());
        spikeReq.setMetricName("CPU_USAGE");
        spikeReq.setMetricValue(95.0);
        spikeReq.setUnit("PERCENT");
        spikeReq.setRecordedAt(baseTime.plusSeconds(120));

        ServiceMetric spikeMetric = metricService.submitMetric(spikeReq);
        testCreatedMetricIds.add(spikeMetric.getId());

        List<AnomalyEvent> initialAnomalies = anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
                testService.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertEquals(1, initialAnomalies.size());
        testCreatedAnomalyIds.add(initialAnomalies.get(0).getId());

        // Step 3: Explicitly re-evaluate the same metric via AnomalyDetectionService
        Optional<AnomalyEvent> duplicateResult = anomalyDetectionService.detectAnomaly(spikeMetric);
        assertTrue(duplicateResult.isEmpty(), "Re-evaluating an already evaluated metric must return Optional.empty()");

        List<AnomalyEvent> anomaliesAfterReeval = anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
                testService.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertEquals(1, anomaliesAfterReeval.size(), "Total anomaly count must remain exactly 1");

        // Step 4: Directly verify that the database unique constraint uk_anomaly_events_metric_id rejects duplicate metric_id
        AnomalyEvent duplicateRow = new AnomalyEvent();
        duplicateRow.setService(testService);
        duplicateRow.setMetric(spikeMetric);
        duplicateRow.setMetricName("CPU_USAGE");
        duplicateRow.setUnit("PERCENT");
        duplicateRow.setObservedValue(95.0);
        duplicateRow.setBaselineMean(50.0);
        duplicateRow.setBaselineStdDev(1.0);
        duplicateRow.setAnomalyScore(45.0);
        duplicateRow.setSeverity("CRITICAL");
        duplicateRow.setDirection("POSITIVE_SPIKE");
        duplicateRow.setStatus("DETECTED");
        duplicateRow.setMessage("Spike detected");
        duplicateRow.setDetectedAt(Instant.now());

        assertThrows(DataIntegrityViolationException.class, () -> {
            anomalyEventRepository.saveAndFlush(duplicateRow);
        }, "Database unique constraint on metric_id must prevent duplicate anomaly rows");
    }

    @Test
    @WithMockUser(username = OWNER_EMAIL, roles = {"USER"})
    void metricIngestion_WhenAnomalyPersistenceFails_MetricAndAlertRemainCommittedWithoutUnexpectedRollback() {
        // Step 1: Create a static threshold alert rule for CPU > 70%
        AlertRule rule = new AlertRule();
        rule.setService(testService);
        rule.setRuleName("High CPU Critical Alert");
        rule.setMetricName("CPU_USAGE");
        rule.setUnit("PERCENT");
        rule.setComparator("GREATER_THAN");
        rule.setThresholdValue(70.0);
        rule.setSeverity("HIGH");
        rule.setActive(true);
        alertRuleRepository.save(rule);

        // Step 2: Ingest 10 baseline metrics at 50% CPU
        Instant baseTime = Instant.now().minusSeconds(1000);
        for (int i = 0; i < 10; i++) {
            CreateMetricRequest req = new CreateMetricRequest();
            req.setServiceId(testService.getId());
            req.setMetricName("CPU_USAGE");
            req.setMetricValue(50.0);
            req.setUnit("PERCENT");
            req.setRecordedAt(baseTime.plusSeconds(i * 10));

            ServiceMetric saved = metricService.submitMetric(req);
            testCreatedMetricIds.add(saved.getId());
        }

        // Verify baseline alone triggered 0 alerts and 0 anomalies
        List<AlertEvent> initialAlerts = alertEventRepository.findByServiceIdOrderByTriggeredAtDescIdDesc(testService.getId());
        assertTrue(initialAlerts.isEmpty(), "No alerts expected during normal baseline ingestion");

        List<AnomalyEvent> initialAnomalies = anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
                testService.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertTrue(initialAnomalies.isEmpty(), "No anomalies expected during normal baseline ingestion");

        // Step 3: Induce a genuine physical database constraint failure on anomaly_events table
        // Any INSERT into anomaly_events will fail with an SQL check constraint violation (1 = 0 is always false)
        jdbcTemplate.execute("ALTER TABLE anomaly_events ADD CONSTRAINT check_anomaly_fail CHECK (1 = 0)");

        ServiceMetric spikeMetric;
        try {
            // Step 4: Submit 11th metric: CPU = 95.0% (> 70% threshold alert AND |Z| = 45.0 anomaly spike)
            CreateMetricRequest spikeReq = new CreateMetricRequest();
            spikeReq.setServiceId(testService.getId());
            spikeReq.setMetricName("CPU_USAGE");
            spikeReq.setMetricValue(95.0);
            spikeReq.setUnit("PERCENT");
            spikeReq.setRecordedAt(baseTime.plusSeconds(120));

            // Must NOT throw UnexpectedRollbackException!
            spikeMetric = metricService.submitMetric(spikeReq);
            testCreatedMetricIds.add(spikeMetric.getId());
        } finally {
            // Drop constraint so subsequent operations can function
            jdbcTemplate.execute("ALTER TABLE anomaly_events DROP CONSTRAINT IF EXISTS check_anomaly_fail");
        }

        // Step 5: Verify that the primary metric was committed successfully despite anomaly failure
        assertNotNull(spikeMetric);
        ServiceMetric reloadedMetric = metricRepository.findById(spikeMetric.getId()).orElse(null);
        assertNotNull(reloadedMetric, "ServiceMetric must remain committed in database");
        assertEquals(95.0, reloadedMetric.getMetricValue());

        // Step 6: Verify that the static threshold alert was committed successfully despite anomaly failure
        List<AlertEvent> alertsAfterSpike = alertEventRepository.findByServiceIdOrderByTriggeredAtDescIdDesc(testService.getId());
        assertEquals(1, alertsAfterSpike.size(), "Static threshold AlertEvent must remain committed in database");
        AlertEvent alert = alertsAfterSpike.get(0);
        assertEquals("TRIGGERED", alert.getStatus());
        assertEquals(95.0, alert.getTriggerValue());
        assertEquals("HIGH", alert.getSeverity());

        // Step 7: Verify that anomaly_events table has 0 rows for this metric because its persistence failed
        List<AnomalyEvent> anomaliesAfterFailure = anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
                testService.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertTrue(anomaliesAfterFailure.isEmpty(), "Anomaly event must not exist due to database failure");

        // Step 8: Verify that once the failure condition is removed, subsequent anomaly detection succeeds
        CreateMetricRequest recoveryReq = new CreateMetricRequest();
        recoveryReq.setServiceId(testService.getId());
        recoveryReq.setMetricName("CPU_USAGE");
        recoveryReq.setMetricValue(96.0);
        recoveryReq.setUnit("PERCENT");
        recoveryReq.setRecordedAt(baseTime.plusSeconds(140));

        ServiceMetric recoveryMetric = metricService.submitMetric(recoveryReq);
        testCreatedMetricIds.add(recoveryMetric.getId());

        List<AnomalyEvent> anomaliesAfterRecovery = anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
                testService.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertEquals(1, anomaliesAfterRecovery.size(), "Subsequent anomaly detection must persist when DB is healthy");
        testCreatedAnomalyIds.add(anomaliesAfterRecovery.get(0).getId());
        assertEquals(96.0, anomaliesAfterRecovery.get(0).getObservedValue());
    }
}
