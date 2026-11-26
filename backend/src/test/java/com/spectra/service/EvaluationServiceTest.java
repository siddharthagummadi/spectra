package com.spectra.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.spectra.dto.EvaluationExperimentResponse;
import com.spectra.dto.RunEvaluationRequest;
import com.spectra.entity.EvaluationExperiment;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.ServiceMetric;
import com.spectra.entity.User;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.EvaluationExperimentRepository;
import com.spectra.repository.ServiceMetricRepository;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock
    private EvaluationExperimentRepository experimentRepository;

    @Mock
    private ServiceMetricRepository metricRepository;

    @InjectMocks
    private EvaluationService evaluationService;

    private User testUser;
    private MonitoredService testService;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("tester@spectra.local");
        testUser.setRole(Role.ADMIN);

        testService = new MonitoredService();
        testService.setId(10L);
        testService.setName("order-service");
    }

    private ServiceMetric createMetric(String name, Double value, String groundTruth, String scenario) {
        ServiceMetric m = new ServiceMetric();
        m.setService(testService);
        m.setMetricName(name);
        m.setMetricValue(value);
        m.setGroundTruthLabel(groundTruth);
        m.setScenarioLabel(scenario);
        m.setRecordedAt(Instant.now());
        return m;
    }

    @Test
    @DisplayName("Should calculate real confusion matrix and metrics accurately")
    void testRunEvaluation_CalculatesMetricsAccurately() {
        List<ServiceMetric> sampleMetrics = new ArrayList<>();
        // 2 True Positives for Static Threshold (CPU > 80 is an anomaly)
        sampleMetrics.add(createMetric("CPU_USAGE", 95.0, "ANOMALY", "CPU_SPIKE"));
        sampleMetrics.add(createMetric("CPU_USAGE", 85.0, "ANOMALY", "CPU_SPIKE"));
        // 1 False Positive (CPU > 80, but ground truth is NORMAL)
        sampleMetrics.add(createMetric("CPU_USAGE", 82.0, "NORMAL", "NORMAL_PEAK"));
        // 2 True Negatives (CPU <= 80, ground truth is NORMAL)
        sampleMetrics.add(createMetric("CPU_USAGE", 45.0, "NORMAL", "BASELINE"));
        sampleMetrics.add(createMetric("CPU_USAGE", 50.0, "NORMAL", "BASELINE"));
        // 1 False Negative (CPU <= 80, but ground truth is ANOMALY)
        sampleMetrics.add(createMetric("CPU_USAGE", 70.0, "ANOMALY", "STEALTH_LEAK"));

        when(metricRepository.findAll()).thenReturn(sampleMetrics);
        when(experimentRepository.save(any(EvaluationExperiment.class))).thenAnswer(inv -> {
            EvaluationExperiment e = inv.getArgument(0);
            e.setId(99L);
            return e;
        });

        RunEvaluationRequest request = new RunEvaluationRequest();
        request.setAlgorithm("STATIC_THRESHOLD");
        request.setDatasetName("Synthetic Benchmark V1");

        List<EvaluationExperimentResponse> results = evaluationService.runEvaluation(request, testUser);

        assertNotNull(results);
        assertEquals(1, results.size());
        EvaluationExperimentResponse exp = results.get(0);

        assertEquals("STATIC_THRESHOLD", exp.getAlgorithm());
        assertEquals(6, exp.getTotalSamples());
        assertEquals(2, exp.getTp(), "Expected 2 True Positives");
        assertEquals(2, exp.getTn(), "Expected 2 True Negatives");
        assertEquals(1, exp.getFp(), "Expected 1 False Positive");
        assertEquals(1, exp.getFn(), "Expected 1 False Negative");

        // Precision = 2 / (2 + 1) = 0.667
        assertEquals(0.667, exp.getPrecisionVal(), 0.005);
        // Recall = 2 / (2 + 1) = 0.667
        assertEquals(0.667, exp.getRecallVal(), 0.005);
        // Accuracy = (2 + 2) / 6 = 0.667
        assertEquals(0.667, exp.getAccuracyVal(), 0.005);
        // Specificity = 2 / (2 + 1) = 0.667
        assertEquals(0.667, exp.getSpecificityVal(), 0.005);
        // FPR = 1 / (1 + 2) = 0.333
        assertEquals(0.333, exp.getFprVal(), 0.005);
    }

    @Test
    @DisplayName("Should handle zero denominators safely without NaN or division by zero")
    void testRunEvaluation_ZeroDenominatorsHandledSafely() {
        List<ServiceMetric> sampleMetrics = new ArrayList<>();
        // All NORMAL, no actual anomalies and no predicted anomalies
        sampleMetrics.add(createMetric("CPU_USAGE", 30.0, "NORMAL", "BASELINE"));
        sampleMetrics.add(createMetric("CPU_USAGE", 40.0, "NORMAL", "BASELINE"));

        when(metricRepository.findAll()).thenReturn(sampleMetrics);
        when(experimentRepository.save(any(EvaluationExperiment.class))).thenAnswer(inv -> inv.getArgument(0));

        RunEvaluationRequest request = new RunEvaluationRequest();
        request.setAlgorithm("STATIC_THRESHOLD");

        List<EvaluationExperimentResponse> results = evaluationService.runEvaluation(request, testUser);

        assertNotNull(results);
        assertEquals(1, results.size());
        EvaluationExperimentResponse exp = results.get(0);

        assertEquals(0, exp.getTp());
        assertEquals(2, exp.getTn());
        assertEquals(0, exp.getFp());
        assertEquals(0, exp.getFn());
        assertEquals(0.0, exp.getPrecisionVal());
        assertEquals(0.0, exp.getRecallVal());
        assertEquals(0.0, exp.getF1Score());
        assertEquals(1.0, exp.getAccuracyVal());
        assertEquals(0.0, exp.getFprVal());
        assertEquals(1.0, exp.getSpecificityVal());
    }

    @Test
    @DisplayName("Should throw BadRequestException if dataset contains no ground truth labels")
    void testRunEvaluation_ThrowsWhenNoGroundTruthLabels() {
        List<ServiceMetric> unlabelledMetrics = new ArrayList<>();
        unlabelledMetrics.add(createMetric("CPU_USAGE", 50.0, null, null));
        unlabelledMetrics.add(createMetric("CPU_USAGE", 60.0, "", null));

        when(metricRepository.findAll()).thenReturn(unlabelledMetrics);

        RunEvaluationRequest request = new RunEvaluationRequest();
        assertThrows(BadRequestException.class, () -> evaluationService.runEvaluation(request, testUser));
    }

    @Test
    @DisplayName("Should evaluate all 3 algorithms when algorithm is ALL")
    void testRunEvaluation_RunsAllAlgorithms() {
        List<ServiceMetric> sampleMetrics = new ArrayList<>();
        sampleMetrics.add(createMetric("CPU_USAGE", 85.0, "ANOMALY", "SPIKE"));
        sampleMetrics.add(createMetric("CPU_USAGE", 30.0, "NORMAL", "NORMAL"));

        when(metricRepository.findAll()).thenReturn(sampleMetrics);
        when(experimentRepository.save(any(EvaluationExperiment.class))).thenAnswer(inv -> inv.getArgument(0));

        RunEvaluationRequest request = new RunEvaluationRequest();
        request.setAlgorithm("ALL");

        List<EvaluationExperimentResponse> results = evaluationService.runEvaluation(request, testUser);

        assertEquals(3, results.size());
        assertTrue(results.stream().anyMatch(r -> r.getAlgorithm().equals("STATIC_THRESHOLD")));
        assertTrue(results.stream().anyMatch(r -> r.getAlgorithm().equals("STATISTICAL_ZSCORE")));
        assertTrue(results.stream().anyMatch(r -> r.getAlgorithm().equals("SPECTRA_AI")));
    }

    @Test
    @DisplayName("Should export experiment results as valid CSV")
    void testExportCsv() {
        EvaluationExperiment exp = new EvaluationExperiment();
        exp.setId(101L);
        exp.setExperimentName("Benchmark Run #1");
        exp.setDatasetName("Production Sample");
        exp.setAlgorithm("SPECTRA_AI");
        exp.setTotalSamples(100);
        exp.setTp(20);
        exp.setTn(75);
        exp.setFp(3);
        exp.setFn(2);
        exp.setPrecisionVal(0.87);
        exp.setRecallVal(0.91);
        exp.setF1Score(0.89);
        exp.setAccuracyVal(0.95);
        exp.setFprVal(0.038);
        exp.setSpecificityVal(0.962);
        exp.setEvaluatedBy("admin@spectra.local");
        exp.setCreatedAt(Instant.now());

        when(experimentRepository.findById(101L)).thenReturn(Optional.of(exp));

        String csv = evaluationService.exportCsv(101L);
        assertNotNull(csv);
        assertTrue(csv.contains("Experiment ID,Experiment Name"));
        assertTrue(csv.contains("Benchmark Run #1"));
        assertTrue(csv.contains("SPECTRA_AI"));
        assertTrue(csv.contains("0.89"));
    }
}
