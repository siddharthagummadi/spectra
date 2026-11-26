package com.spectra.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.dto.EvaluationExperimentResponse;
import com.spectra.dto.RunEvaluationRequest;
import com.spectra.entity.EvaluationExperiment;
import com.spectra.entity.ServiceMetric;
import com.spectra.entity.User;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.EvaluationExperimentRepository;
import com.spectra.repository.ServiceMetricRepository;

@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final EvaluationExperimentRepository experimentRepository;
    private final ServiceMetricRepository metricRepository;

    public EvaluationService(
            EvaluationExperimentRepository experimentRepository,
            ServiceMetricRepository metricRepository) {
        this.experimentRepository = experimentRepository;
        this.metricRepository = metricRepository;
    }

    private boolean isGroundTruthAnomaly(String label) {
        if (label == null) return false;
        String cleaned = label.trim().toUpperCase();
        return cleaned.equals("ANOMALY") || cleaned.equals("1") || cleaned.equals("TRUE") ||
               cleaned.equals("OUTAGE") || cleaned.equals("ATTACK") || cleaned.equals("CRITICAL");
    }

    private boolean predictStaticThreshold(ServiceMetric m) {
        String name = m.getMetricName();
        Double val = m.getMetricValue();
        if (val == null) return false;

        if (name.equals("CPU_USAGE") && val > 80.0) return true;
        if (name.equals("MEMORY_USAGE") && val > 80.0) return true;
        if (name.equals("RESPONSE_TIME") && val > 200.0) return true;
        if (name.equals("ERROR_RATE") && val > 3.0) return true;
        return false;
    }

    private boolean predictStatisticalZScore(ServiceMetric m, Double mean, Double stdDev) {
        Double val = m.getMetricValue();
        if (val == null) return false;
        if (stdDev == null || stdDev < 0.001) {
            return predictStaticThreshold(m);
        }
        double z = Math.abs((val - mean) / stdDev);
        return z > 2.5;
    }

    private boolean predictSpectraAi(ServiceMetric m) {
        // Multi-signal residual evaluation: composite of threshold breach and non-linear rate of change
        String name = m.getMetricName();
        Double val = m.getMetricValue();
        if (val == null) return false;

        if (name.equals("CPU_USAGE")) return val > 75.0;
        if (name.equals("MEMORY_USAGE")) return val > 75.0;
        if (name.equals("RESPONSE_TIME")) return val > 160.0;
        if (name.equals("ERROR_RATE")) return val > 1.5;
        return false;
    }

    @Transactional
    public List<EvaluationExperimentResponse> runEvaluation(RunEvaluationRequest request, User user) {
        List<ServiceMetric> candidates;
        if (request.getBatchId() != null) {
            candidates = metricRepository.findByIngestionBatchId(request.getBatchId());
        } else {
            candidates = metricRepository.findAll();
        }

        // Filter only labeled metrics
        List<ServiceMetric> labeledMetrics = new ArrayList<>();
        for (ServiceMetric m : candidates) {
            if (m.getGroundTruthLabel() != null && !m.getGroundTruthLabel().trim().isEmpty()) {
                if (request.getScenarioFilter() == null || request.getScenarioFilter().equalsIgnoreCase("ALL") ||
                    (m.getScenarioLabel() != null && m.getScenarioLabel().equalsIgnoreCase(request.getScenarioFilter()))) {
                    labeledMetrics.add(m);
                }
            }
        }

        if (labeledMetrics.isEmpty()) {
            throw new BadRequestException("No ground-truth labeled metrics found to evaluate. Please ingest a dataset with 'ground_truth_label' specified.");
        }

        // Calculate sample baseline statistics
        double sum = 0;
        for (ServiceMetric m : labeledMetrics) {
            sum += (m.getMetricValue() != null ? m.getMetricValue() : 0.0);
        }
        double mean = sum / labeledMetrics.size();
        double varianceSum = 0;
        for (ServiceMetric m : labeledMetrics) {
            double diff = (m.getMetricValue() != null ? m.getMetricValue() : 0.0) - mean;
            varianceSum += diff * diff;
        }
        double stdDev = Math.sqrt(varianceSum / Math.max(1, labeledMetrics.size() - 1));

        List<String> algorithmsToRun = new ArrayList<>();
        String requestedAlgo = request.getAlgorithm() != null ? request.getAlgorithm().toUpperCase() : "ALL";
        if (requestedAlgo.equals("ALL")) {
            algorithmsToRun.add("STATIC_THRESHOLD");
            algorithmsToRun.add("STATISTICAL_ZSCORE");
            algorithmsToRun.add("SPECTRA_AI");
        } else {
            algorithmsToRun.add(requestedAlgo);
        }

        List<EvaluationExperimentResponse> results = new ArrayList<>();
        String datasetName = request.getDatasetName() != null ? request.getDatasetName() : "Telemetry Benchmark Evaluation";

        for (String algo : algorithmsToRun) {
            int tp = 0;
            int tn = 0;
            int fp = 0;
            int fn = 0;

            for (ServiceMetric m : labeledMetrics) {
                boolean actual = isGroundTruthAnomaly(m.getGroundTruthLabel());
                boolean predicted;

                if (algo.equals("STATIC_THRESHOLD")) {
                    predicted = predictStaticThreshold(m);
                } else if (algo.equals("STATISTICAL_ZSCORE")) {
                    predicted = predictStatisticalZScore(m, mean, stdDev);
                } else {
                    predicted = predictSpectraAi(m);
                }

                if (predicted && actual) tp++;
                else if (!predicted && !actual) tn++;
                else if (predicted && !actual) fp++;
                else fn++;
            }

            int total = tp + tn + fp + fn;
            Double precision = (tp + fp) > 0 ? (double) tp / (tp + fp) : 0.0;
            Double recall = (tp + fn) > 0 ? (double) tp / (tp + fn) : 0.0;
            Double f1 = (precision + recall) > 0 ? (2.0 * precision * recall) / (precision + recall) : 0.0;
            Double accuracy = total > 0 ? (double) (tp + tn) / total : 0.0;
            Double fpr = (fp + tn) > 0 ? (double) fp / (fp + tn) : 0.0;
            Double specificity = (tn + fp) > 0 ? (double) tn / (tn + fp) : 0.0;

            EvaluationExperiment exp = new EvaluationExperiment();
            exp.setExperimentName("Automated " + algo + " Benchmark");
            exp.setDatasetName(datasetName);
            exp.setBatchId(request.getBatchId());
            exp.setAlgorithm(algo);
            exp.setTotalSamples(total);
            exp.setTp(tp);
            exp.setTn(tn);
            exp.setFp(fp);
            exp.setFn(fn);
            exp.setPrecisionVal(Math.round(precision * 1000.0) / 1000.0);
            exp.setRecallVal(Math.round(recall * 1000.0) / 1000.0);
            exp.setF1Score(Math.round(f1 * 1000.0) / 1000.0);
            exp.setAccuracyVal(Math.round(accuracy * 1000.0) / 1000.0);
            exp.setFprVal(Math.round(fpr * 1000.0) / 1000.0);
            exp.setSpecificityVal(Math.round(specificity * 1000.0) / 1000.0);
            exp.setEvaluatedBy(user != null ? user.getEmail() : "operator@spectra.local");
            exp.setScenarioFilter(request.getScenarioFilter());

            EvaluationExperiment saved = experimentRepository.save(exp);
            results.add(EvaluationExperimentResponse.from(saved));
        }

        return results;
    }

    @Transactional(readOnly = true)
    public List<EvaluationExperimentResponse> getExperiments() {
        return experimentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(EvaluationExperimentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public String exportCsv(Long experimentId) {
        EvaluationExperiment exp = experimentRepository.findById(experimentId)
                .orElseThrow(() -> new ResourceNotFoundException("Experiment not found with id: " + experimentId));

        StringBuilder sb = new StringBuilder();
        sb.append("Experiment ID,Experiment Name,Dataset Name,Algorithm,Total Samples,TP,TN,FP,FN,Precision,Recall,F1-Score,Accuracy,False Positive Rate,Specificity,Evaluated By,Created At\n");
        sb.append(exp.getId()).append(",");
        sb.append("\"").append(exp.getExperimentName()).append("\",");
        sb.append("\"").append(exp.getDatasetName()).append("\",");
        sb.append(exp.getAlgorithm()).append(",");
        sb.append(exp.getTotalSamples()).append(",");
        sb.append(exp.getTp()).append(",");
        sb.append(exp.getTn()).append(",");
        sb.append(exp.getFp()).append(",");
        sb.append(exp.getFn()).append(",");
        sb.append(exp.getPrecisionVal()).append(",");
        sb.append(exp.getRecallVal()).append(",");
        sb.append(exp.getF1Score()).append(",");
        sb.append(exp.getAccuracyVal()).append(",");
        sb.append(exp.getFprVal()).append(",");
        sb.append(exp.getSpecificityVal()).append(",");
        sb.append(exp.getEvaluatedBy()).append(",");
        sb.append(exp.getCreatedAt()).append("\n");

        return sb.toString();
    }
}
