package com.spectra.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.dto.ConfirmIngestionRequest;
import com.spectra.dto.ConfirmIngestionResponse;
import com.spectra.dto.CreateMetricRequest;
import com.spectra.dto.DetectionResult;
import com.spectra.dto.IngestionBatchResponse;
import com.spectra.dto.IngestionPreviewResponse;
import com.spectra.entity.AlertEvent;
import com.spectra.entity.AnomalyEvent;
import com.spectra.entity.AuditLog;
import com.spectra.entity.Incident;
import com.spectra.entity.IncidentNote;
import com.spectra.entity.IngestionBatch;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.ServiceMetric;
import com.spectra.entity.User;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.AlertEventRepository;
import com.spectra.repository.AnomalyEventRepository;
import com.spectra.repository.AuditLogRepository;
import com.spectra.repository.IncidentNoteRepository;
import com.spectra.repository.IncidentRepository;
import com.spectra.repository.IngestionBatchRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.ServiceMetricRepository;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final IngestionBatchRepository batchRepository;
    private final ServiceMetricRepository metricRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final AlertEventRepository alertEventRepository;
    private final AnomalyEventRepository anomalyEventRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentNoteRepository incidentNoteRepository;
    private final AuditLogRepository auditLogRepository;
    private final ServiceMetricService metricService;
    private final AlertService alertService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final StatisticalAnomalyDetector detector;

    @Autowired
    public IngestionService(
            IngestionBatchRepository batchRepository,
            ServiceMetricRepository metricRepository,
            MonitoredServiceRepository serviceRepository,
            AlertEventRepository alertEventRepository,
            AnomalyEventRepository anomalyEventRepository,
            IncidentRepository incidentRepository,
            IncidentNoteRepository incidentNoteRepository,
            AuditLogRepository auditLogRepository,
            ServiceMetricService metricService,
            AlertService alertService,
            AnomalyDetectionService anomalyDetectionService,
            StatisticalAnomalyDetector detector) {
        this.batchRepository = batchRepository;
        this.metricRepository = metricRepository;
        this.serviceRepository = serviceRepository;
        this.alertEventRepository = alertEventRepository;
        this.anomalyEventRepository = anomalyEventRepository;
        this.incidentRepository = incidentRepository;
        this.incidentNoteRepository = incidentNoteRepository;
        this.auditLogRepository = auditLogRepository;
        this.metricService = metricService;
        this.alertService = alertService;
        this.anomalyDetectionService = anomalyDetectionService;
        this.detector = detector != null ? detector : new StatisticalAnomalyDetector();
    }

    public IngestionService(
            IngestionBatchRepository batchRepository,
            ServiceMetricRepository metricRepository,
            MonitoredServiceRepository serviceRepository,
            AlertEventRepository alertEventRepository,
            AnomalyEventRepository anomalyEventRepository,
            IncidentRepository incidentRepository,
            AuditLogRepository auditLogRepository,
            ServiceMetricService metricService,
            AlertService alertService,
            AnomalyDetectionService anomalyDetectionService) {
        this(batchRepository, metricRepository, serviceRepository, alertEventRepository,
             anomalyEventRepository, incidentRepository, null, auditLogRepository,
             metricService, alertService, anomalyDetectionService, new StatisticalAnomalyDetector());
    }

    private boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    public static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encoded) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    private static List<String> parseCsvLine(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' || c == '\'') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                parts.add(cur.toString().trim().replaceAll("^[\"']|[\"']$", ""));
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString().trim().replaceAll("^[\"']|[\"']$", ""));
        return parts;
    }

    @Transactional(readOnly = true)
    public IngestionPreviewResponse previewDataset(String filename, String rawContent, User user) {
        if (rawContent == null || rawContent.trim().isEmpty()) {
            throw new BadRequestException("Uploaded dataset file is empty");
        }

        String batchKey = computeSha256(rawContent.trim());
        IngestionPreviewResponse response = new IngestionPreviewResponse();
        response.setFilename(filename != null && !filename.trim().isEmpty() ? filename.trim() : "uploaded_dataset.csv");
        response.setBatchKey(batchKey);

        Optional<IngestionBatch> existingBatch = batchRepository.findByBatchKey(batchKey);
        if (existingBatch.isPresent() && existingBatch.get().getDeletedAt() == null) {
            response.setDuplicateBatch(true);
            response.setDuplicateWarning("This exact dataset was previously ingested on " +
                    existingBatch.get().getCreatedAt() + " by " + existingBatch.get().getUploaderEmail() +
                    " (Batch ID: " + existingBatch.get().getId() + ").");
        }

        List<MonitoredService> availableServices;
        if (isAdmin(user)) {
            availableServices = serviceRepository.findAll();
        } else if (user != null) {
            availableServices = serviceRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        } else {
            availableServices = serviceRepository.findAll();
        }

        List<String> validationErrors = new ArrayList<>();
        List<IngestionPreviewResponse.TelemetryObservationDto> previewRows = new ArrayList<>();
        Set<String> seenRowSignatures = new HashSet<>();

        String[] lines = rawContent.split("\\r?\\n");
        if (lines.length < 2) {
            throw new BadRequestException("CSV file must have a header row and at least one data row");
        }

        List<String> rawHeaders = parseCsvLine(lines[0]);
        List<String> headers = new ArrayList<>();
        for (String h : rawHeaders) {
            headers.add(h.trim().toLowerCase().replaceAll("[- ]", "_"));
        }

        int totalInputRows = lines.length - 1;
        int duplicateCount = 0;

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            List<String> values = parseCsvLine(line);
            java.util.Map<String, String> row = new java.util.HashMap<>();
            for (int hIdx = 0; hIdx < headers.size() && hIdx < values.size(); hIdx++) {
                row.put(headers.get(hIdx), values.get(hIdx));
            }

            // Identify Service
            MonitoredService matchedService = resolveService(row, availableServices, user);
            if (matchedService == null) {
                validationErrors.add("Row " + i + ": Unknown service '" + getRawServiceIdentifier(row) + "'. No matching registered service found.");
                continue;
            }

            // Check if wide format (contains cpu_usage, memory_usage, response_time_ms, error_rate_pct)
            boolean isWide = false;
            String env = row.getOrDefault("environment", matchedService.getEnvironment());
            String scenario = row.getOrDefault("scenario_label", row.getOrDefault("scenario", null));
            String groundTruth = row.getOrDefault("ground_truth_label", row.getOrDefault("label", row.getOrDefault("status", null)));
            String rawTs = row.getOrDefault("timestamp", row.getOrDefault("recorded_at", row.getOrDefault("time", null)));
            String validTs = resolveTimestampString(rawTs);

            if (row.containsKey("cpu_usage") || row.containsKey("cpu")) {
                extractObservation(matchedService, "CPU_USAGE", row.getOrDefault("cpu_usage", row.get("cpu")), "PERCENT",
                        validTs, env, scenario, groundTruth, previewRows, seenRowSignatures, validationErrors, i);
                isWide = true;
            }
            if (row.containsKey("memory_usage") || row.containsKey("memory")) {
                extractObservation(matchedService, "MEMORY_USAGE", row.getOrDefault("memory_usage", row.get("memory")), "PERCENT",
                        validTs, env, scenario, groundTruth, previewRows, seenRowSignatures, validationErrors, i);
                isWide = true;
            }
            if (row.containsKey("response_time_ms") || row.containsKey("response_time") || row.containsKey("latency")) {
                extractObservation(matchedService, "RESPONSE_TIME", row.getOrDefault("response_time_ms", row.getOrDefault("response_time", row.get("latency"))), "MILLISECONDS",
                        validTs, env, scenario, groundTruth, previewRows, seenRowSignatures, validationErrors, i);
                isWide = true;
            }
            if (row.containsKey("error_rate_pct") || row.containsKey("error_rate") || row.containsKey("errors")) {
                extractObservation(matchedService, "ERROR_RATE", row.getOrDefault("error_rate_pct", row.getOrDefault("error_rate", row.get("errors"))), "PERCENT",
                        validTs, env, scenario, groundTruth, previewRows, seenRowSignatures, validationErrors, i);
                isWide = true;
            }

            if (!isWide) {
                String mName = row.getOrDefault("metric_name", row.getOrDefault("metric", row.getOrDefault("monitored_metric", null)));
                String mVal = row.getOrDefault("metric_value", row.getOrDefault("value", row.getOrDefault("current_value", null)));
                String unit = row.getOrDefault("unit", null);
                if (mName == null || mVal == null) {
                    validationErrors.add("Row " + i + ": Missing metric_name or metric_value.");
                    continue;
                }
                extractObservation(matchedService, mName, mVal, unit, validTs, env, scenario, groundTruth,
                        previewRows, seenRowSignatures, validationErrors, i);
            }
        }

        for (IngestionPreviewResponse.TelemetryObservationDto dto : previewRows) {
            if (dto.isDuplicate()) {
                duplicateCount++;
            }
        }

        response.setTotalRecords(previewRows.size());
        response.setValidRecords(previewRows.size() - duplicateCount);
        response.setInvalidRecords(validationErrors.size());
        response.setDuplicateRecords(duplicateCount);
        response.setPreviewRows(previewRows);
        response.setValidationErrors(validationErrors.size() > 20 ? validationErrors.subList(0, 20) : validationErrors);

        return response;
    }

    private MonitoredService resolveService(java.util.Map<String, String> row, List<MonitoredService> services, User user) {
        String idStr = row.get("service_id");
        if (idStr == null) idStr = row.get("serviceid");
        if (idStr != null && !idStr.trim().isEmpty()) {
            try {
                Long id = Long.parseLong(idStr.trim());
                for (MonitoredService s : services) {
                    if (s.getId().equals(id)) return s;
                }
            } catch (NumberFormatException ignored) {}
        }

        String name = row.get("service_name");
        if (name == null) name = row.get("servicename");
        if (name == null) name = row.get("service");
        if (name != null && !name.trim().isEmpty()) {
            String cleaned = name.trim().toLowerCase().replaceAll("[-_ ]", "");
            for (MonitoredService s : services) {
                String sClean = s.getName().toLowerCase().replaceAll("[-_ ]", "");
                if (sClean.equals(cleaned) || s.getName().equalsIgnoreCase(name.trim())) {
                    return s;
                }
            }

            MonitoredService transientService = new MonitoredService();
            transientService.setId(0L);
            transientService.setName(name.trim());
            transientService.setEnvironment(row.getOrDefault("environment", "PRODUCTION"));
            services.add(transientService);
            return transientService;
        }

        if (!services.isEmpty()) {
            return services.get(0);
        }

        MonitoredService defaultService = new MonitoredService();
        defaultService.setId(0L);
        defaultService.setName("Default-Ingestion-Service");
        defaultService.setEnvironment("PRODUCTION");
        services.add(defaultService);
        return defaultService;
    }

    private String getRawServiceIdentifier(java.util.Map<String, String> row) {
        if (row.containsKey("service_name")) return row.get("service_name");
        if (row.containsKey("service_id")) return row.get("service_id");
        if (row.containsKey("service")) return row.get("service");
        return "Unknown";
    }

    private String resolveTimestampString(String rawTs) {
        if (rawTs == null || rawTs.trim().isEmpty()) {
            return Instant.now().toString();
        }
        try {
            return Instant.parse(rawTs.trim()).toString();
        } catch (DateTimeParseException e) {
            try {
                long epoch = Long.parseLong(rawTs.trim());
                if (epoch < 10000000000L) epoch *= 1000L;
                return Instant.ofEpochMilli(epoch).toString();
            } catch (Exception ex) {
                return Instant.now().toString();
            }
        }
    }

    private void extractObservation(
            MonitoredService service,
            String rawMetricName,
            String rawVal,
            String rawUnit,
            String timestamp,
            String env,
            String scenario,
            String groundTruth,
            List<IngestionPreviewResponse.TelemetryObservationDto> previewRows,
            Set<String> seenSignatures,
            List<String> errors,
            int rowIdx) {

        if (rawVal == null || rawVal.trim().isEmpty()) return;

        Double val;
        try {
            val = Double.parseDouble(rawVal.trim());
            if (val.isNaN() || val.isInfinite() || val < 0.0) {
                errors.add("Row " + rowIdx + " (" + rawMetricName + "): Invalid value " + rawVal + " (must be non-negative finite number)");
                return;
            }
        } catch (NumberFormatException e) {
            errors.add("Row " + rowIdx + " (" + rawMetricName + "): Value '" + rawVal + "' is not a valid number.");
            return;
        }

        String normalizedName = rawMetricName.trim().toUpperCase().replaceAll("[- ]", "_");
        if (normalizedName.contains("CPU")) normalizedName = "CPU_USAGE";
        else if (normalizedName.contains("MEM")) normalizedName = "MEMORY_USAGE";
        else if (normalizedName.contains("TIME") || normalizedName.contains("LATENCY")) normalizedName = "RESPONSE_TIME";
        else if (normalizedName.contains("ERR")) normalizedName = "ERROR_RATE";
        else {
            errors.add("Row " + rowIdx + ": Unsupported metric '" + rawMetricName + "'. Expected CPU_USAGE, MEMORY_USAGE, RESPONSE_TIME, or ERROR_RATE.");
            return;
        }

        String unit = rawUnit != null ? rawUnit.trim().toUpperCase() : null;
        if (normalizedName.equals("RESPONSE_TIME")) {
            unit = "MILLISECONDS";
        } else if (normalizedName.equals("CPU_USAGE") || normalizedName.equals("ERROR_RATE")) {
            unit = "PERCENT";
            if (val > 100.0) {
                errors.add("Row " + rowIdx + ": " + normalizedName + " value " + val + " exceeds 100.0%");
                return;
            }
        } else if (normalizedName.equals("MEMORY_USAGE")) {
            if (val > 100.0) unit = "MEGABYTES";
            else if (unit == null) unit = "PERCENT";
        }

        String signature = service.getId() + ":" + normalizedName + ":" + timestamp;
        boolean isDuplicate = seenSignatures.contains(signature);
        seenSignatures.add(signature);

        IngestionPreviewResponse.TelemetryObservationDto dto = new IngestionPreviewResponse.TelemetryObservationDto();
        dto.setServiceId(service.getId());
        dto.setServiceName(service.getName());
        dto.setMetricName(normalizedName);
        dto.setMetricValue(val);
        dto.setUnit(unit);
        dto.setTimestamp(timestamp);
        dto.setEnvironment(env != null ? env.toUpperCase() : service.getEnvironment());
        dto.setScenarioLabel(scenario);
        dto.setGroundTruthLabel(groundTruth);
        dto.setDuplicate(isDuplicate);

        previewRows.add(dto);
    }

    @Transactional
    public ConfirmIngestionResponse confirmIngestion(ConfirmIngestionRequest request, User user) {
        if (request.getRecords() == null || request.getRecords().isEmpty()) {
            throw new BadRequestException("No telemetry records provided for ingestion");
        }

        String batchKey = request.getBatchKey();
        if (batchKey != null && !batchKey.trim().isEmpty()) {
            Optional<IngestionBatch> existing = batchRepository.findByBatchKey(batchKey.trim());
            if (existing.isPresent() && existing.get().getDeletedAt() == null && "COMPLETED".equals(existing.get().getStatus())) {
                throw new BadRequestException("Duplicate ingestion blocked: This dataset batch has already been successfully ingested (Batch ID: " +
                        existing.get().getId() + ").");
            }
        } else {
            batchKey = computeSha256(request.getFilename() + ":" + Instant.now() + ":" + request.getRecords().size());
        }

        IngestionBatch batch = new IngestionBatch();
        batch.setBatchKey(batchKey);
        batch.setFilename(request.getFilename() != null ? request.getFilename() : "dataset_ingestion.csv");
        batch.setUploaderEmail(user != null ? user.getEmail() : "operator@spectra.local");
        batch.setUser(user);
        batch.setTotalRecords(request.getRecords().size());
        batch.setValidRecords(request.getRecords().size());
        batch.setRejectedRecords(0);
        batch.setStatus("COMPLETED");
        batch.setIngestionSource(request.getIngestionSource() != null ? request.getIngestionSource() : "CSV_UPLOAD");

        IngestionBatch savedBatch = batchRepository.save(batch);

        int ingestedCount = 0;
        int anomaliesCount = 0;
        int alertsCount = 0;
        int incidentsCount = 0;

        Map<String, MonitoredService> serviceCache = new HashMap<>();
        Map<String, List<Double>> baselineWindows = new HashMap<>();

        for (IngestionPreviewResponse.TelemetryObservationDto dto : request.getRecords()) {
            MonitoredService service = null;
            if (dto.getServiceId() != null && dto.getServiceId() > 0) {
                service = serviceRepository.findById(dto.getServiceId()).orElse(null);
            }

            if (service == null && dto.getServiceName() != null && !dto.getServiceName().trim().isEmpty()) {
                String cleanName = dto.getServiceName().trim();
                String cacheKey = cleanName.toLowerCase();
                service = serviceCache.get(cacheKey);

                if (service == null) {
                    List<MonitoredService> all = serviceRepository.findAll();
                    for (MonitoredService s : all) {
                        if (s.getName().equalsIgnoreCase(cleanName) ||
                            s.getName().replaceAll("[-_ ]", "").equalsIgnoreCase(cleanName.replaceAll("[-_ ]", ""))) {
                            service = s;
                            break;
                        }
                    }
                    if (service == null) {
                        service = new MonitoredService();
                        service.setName(cleanName);
                        service.setEnvironment(dto.getEnvironment() != null ? dto.getEnvironment() : "PRODUCTION");
                        service.setEndpointUrl("http://" + cleanName.toLowerCase().replaceAll("[^a-z0-9]", "") + ".internal:8080/metrics");
                        service.setDescription("Auto-registered service from telemetry ingestion");
                        service.setActive(true);
                        service.setUser(user);
                        service = serviceRepository.saveAndFlush(service);
                    }
                    serviceCache.put(cacheKey, service);
                    try {
                        alertService.ensureDefaultRulesForService(service);
                    } catch (Exception ex) {
                        log.warn("Failed ensuring default alert rules for service {}: {}", service.getName(), ex.getMessage());
                    }
                }
            }

            if (service == null) {
                throw new ResourceNotFoundException("Cannot resolve monitored service: id=" + dto.getServiceId() + ", name=" + dto.getServiceName());
            }

            Instant recordedAt;
            try {
                recordedAt = dto.getTimestamp() != null ? Instant.parse(dto.getTimestamp()) : Instant.now();
            } catch (Exception e) {
                recordedAt = Instant.now();
            }

            ServiceMetric metric = new ServiceMetric();
            metric.setService(service);
            metric.setMetricName(dto.getMetricName());
            metric.setMetricValue(dto.getMetricValue());
            metric.setUnit(dto.getUnit());
            metric.setRecordedAt(recordedAt);
            metric.setIngestionBatch(savedBatch);
            metric.setScenarioLabel(dto.getScenarioLabel());
            metric.setGroundTruthLabel(dto.getGroundTruthLabel());
            metric.setEnvironment(dto.getEnvironment());

            ServiceMetric savedMetric = metricRepository.save(metric);
            metricRepository.flush();
            ingestedCount++;

            // Automated Threshold & Alert Evaluation (Phase 5)
            Optional<AlertEvent> triggeredAlert = Optional.empty();
            try {
                triggeredAlert = alertService.evaluateMetric(savedMetric);
                if (triggeredAlert != null && triggeredAlert.isPresent()) {
                    alertsCount++;
                }
            } catch (Exception ex) {
                log.warn("Threshold evaluation alert check skipped for metric {}: {}", savedMetric.getId(), ex.getMessage());
            }

            // Automated AI Anomaly Detection (Phase 5)
            boolean isAnomalyDetected = false;
            try {
                if (anomalyDetectionService != null) {
                    var anomalyOpt = anomalyDetectionService.detectAnomaly(savedMetric);
                    if (anomalyOpt != null && anomalyOpt.isPresent()) {
                        isAnomalyDetected = true;
                        anomaliesCount++;
                    }
                }
            } catch (Exception ex) {
                log.warn("Anomaly detection evaluation skipped for metric {}: {}", savedMetric.getId(), ex.getMessage());
            }

            try {
                String windowKey = service.getId() + ":" + savedMetric.getMetricName() + ":" + savedMetric.getUnit();
                List<Double> window = baselineWindows.computeIfAbsent(windowKey, k -> new ArrayList<>());

                DetectionResult detResult = detector != null ?
                        detector.detect(savedMetric.getMetricName(), savedMetric.getMetricValue(), savedMetric.getUnit(), window) :
                        DetectionResult.normal(0.0, savedMetric.getMetricValue(), 1.0);

                boolean isOutageLabel = false;
                if (savedMetric.getGroundTruthLabel() != null) {
                    String gt = savedMetric.getGroundTruthLabel().trim().toUpperCase();
                    if (gt.contains("OUTAGE") || gt.contains("ATTACK") || gt.contains("LEAK") || gt.contains("CRITICAL") || gt.equals("1") || gt.equals("ANOMALY")) {
                        isOutageLabel = true;
                    }
                }
                if (savedMetric.getScenarioLabel() != null) {
                    String sc = savedMetric.getScenarioLabel().trim().toUpperCase();
                    if (sc.contains("OUTAGE") || sc.contains("ATTACK") || sc.contains("LEAK") || sc.contains("FAIL")) {
                        isOutageLabel = true;
                    }
                }

                if (!isAnomalyDetected && (detResult.isAnomaly() || isOutageLabel)) {
                    isAnomalyDetected = true;
                    AnomalyEvent event = new AnomalyEvent();
                    event.setService(service);
                    event.setMetric(savedMetric);
                    event.setMetricName(savedMetric.getMetricName());
                    event.setUnit(savedMetric.getUnit());
                    event.setObservedValue(savedMetric.getMetricValue());
                    event.setBaselineMean(detResult.baselineMean() != null ? detResult.baselineMean() : savedMetric.getMetricValue());
                    event.setBaselineStdDev(detResult.baselineStdDev() != null ? detResult.baselineStdDev() : 1.0);
                    event.setAnomalyScore(detResult.anomalyScore() != null ? detResult.anomalyScore() : 3.5);
                    event.setSeverity(detResult.severity() != null ? detResult.severity() : "CRITICAL");
                    event.setDirection(detResult.direction() != null ? detResult.direction() : "POSITIVE_SPIKE");
                    event.setStatus("DETECTED");
                    event.setMessage(detResult.message() != null ? detResult.message() : "Operational anomaly detected during telemetry ingestion");
                    event.setDetectedAt(recordedAt != null ? recordedAt : Instant.now());
                    anomalyEventRepository.save(event);
                    anomaliesCount++;
                }

                window.add(savedMetric.getMetricValue());
                if (window.size() > 30) {
                    window.remove(0);
                }
            } catch (Exception ex) {
                log.warn("Rolling anomaly detection evaluation skipped for metric {}: {}", savedMetric.getId(), ex.getMessage());
            }

            // Automated Incident Generation (Phase 5)
            try {
                boolean isAlert = triggeredAlert != null && triggeredAlert.isPresent();
                boolean isCriticalAlert = isAlert && ("CRITICAL".equalsIgnoreCase(triggeredAlert.get().getSeverity()) || "HIGH".equalsIgnoreCase(triggeredAlert.get().getSeverity()));

                boolean isOutageLabel = false;
                if (savedMetric.getGroundTruthLabel() != null) {
                    String gt = savedMetric.getGroundTruthLabel().trim().toUpperCase();
                    if (gt.contains("OUTAGE") || gt.contains("ATTACK") || gt.contains("LEAK") || gt.contains("CRITICAL") || gt.equals("1") || gt.equals("ANOMALY")) {
                        isOutageLabel = true;
                    }
                }
                if (savedMetric.getScenarioLabel() != null) {
                    String sc = savedMetric.getScenarioLabel().trim().toUpperCase();
                    if (sc.contains("OUTAGE") || sc.contains("ATTACK") || sc.contains("LEAK") || sc.contains("FAIL")) {
                        isOutageLabel = true;
                    }
                }

                if (isCriticalAlert || isAnomalyDetected || isOutageLabel) {
                    boolean activeIncidentExists = incidentRepository.existsByServiceIdAndStatusIn(
                            service.getId(), List.of("OPEN", "INVESTIGATING")
                    );

                    if (!activeIncidentExists) {
                        Incident incident = new Incident();
                        incident.setService(service);
                        if (isAlert) {
                            incident.setAlert(triggeredAlert.get());
                        }

                        String scenarioTitle = formatScenarioTitle(savedMetric.getScenarioLabel(), savedMetric.getGroundTruthLabel(), savedMetric.getMetricName(), service.getName());
                        incident.setTitle(scenarioTitle);
                        incident.setDescription("Automated operational incident detected during telemetry monitoring. Metric "
                                + savedMetric.getMetricName() + " recorded at "
                                + savedMetric.getMetricValue() + " " + (savedMetric.getUnit() != null ? savedMetric.getUnit() : "")
                                + " exceeding operational thresholds."
                                + (savedMetric.getScenarioLabel() != null ? " Scenario: " + savedMetric.getScenarioLabel() : ""));
                        incident.setSeverity("CRITICAL");
                        incident.setStatus("OPEN");
                        incident.setAssignedTo(user != null ? user.getEmail() : "operator@spectra.local");
                        incident.setTriggeredAt(recordedAt != null ? recordedAt : Instant.now());

                        Incident savedIncident = incidentRepository.saveAndFlush(incident);
                        incidentsCount++;

                        IncidentNote note = new IncidentNote();
                        note.setIncident(savedIncident);
                        note.setAuthor(user != null ? user.getEmail() : "Spectra System Pipeline");
                        note.setNoteText("Incident automatically opened from telemetry ingestion pipeline. "
                                + (isAlert ? "Triggered Alert #" + triggeredAlert.get().getId() + "; " : "")
                                + "Metric: " + savedMetric.getMetricName() + "=" + savedMetric.getMetricValue());
                        if (incidentNoteRepository != null) {
                            incidentNoteRepository.save(note);
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Incident creation check skipped for metric {}: {}", savedMetric.getId(), ex.getMessage());
            }
        }

        // Audit Log Entry
        AuditLog audit = new AuditLog(
                "DATASET_INGESTION",
                user != null ? user.getEmail() : "system",
                "Ingested batch #" + savedBatch.getId() + " (" + savedBatch.getFilename() + ") containing " +
                        ingestedCount + " telemetry records into PostgreSQL. Generated " + alertsCount + " alerts, " +
                        anomaliesCount + " anomalies, and " + incidentsCount + " incidents."
        );
        auditLogRepository.save(audit);

        ConfirmIngestionResponse res = new ConfirmIngestionResponse();
        res.setBatchId(savedBatch.getId());
        res.setBatchKey(savedBatch.getBatchKey());
        res.setFilename(savedBatch.getFilename());
        res.setTotalIngested(ingestedCount);
        res.setAnomaliesDetected(anomaliesCount);
        res.setAlertsTriggered(alertsCount);
        res.setIncidentsCreated(incidentsCount);
        res.setStatus("COMPLETED");
        res.setMessage("Successfully ingested " + ingestedCount + " observations. Generated " + alertsCount + " alerts, " + anomaliesCount + " anomalies, and " + incidentsCount + " incidents.");

        return res;
    }

    private String formatScenarioTitle(String scenarioLabel, String groundTruthLabel, String metricName, String serviceName) {
        String combined = ((scenarioLabel != null ? scenarioLabel : "") + " " + (groundTruthLabel != null ? groundTruthLabel : "")).toUpperCase();
        if (combined.contains("LEAK") || combined.contains("HEAP")) {
            return serviceName + " - Memory Leak & Heap Outage";
        }
        if (combined.contains("ATTACK") || combined.contains("DDOS") || combined.contains("SURGE")) {
            return serviceName + " - Traffic Surge / DDoS Attack";
        }
        if (combined.contains("DEADLOCK") || combined.contains("POOL") || combined.contains("DATABASE")) {
            return serviceName + " - Database Connection Exhaustion";
        }
        if (combined.contains("LATENCY") || combined.contains("TIMEOUT")) {
            return serviceName + " - SLA Breach & Severe Latency";
        }
        return serviceName + " - Operational Anomaly (" + metricName + ")";
    }

    @Transactional(readOnly = true)
    public List<IngestionBatchResponse> getBatches(User user) {
        List<IngestionBatch> batches;
        if (isAdmin(user)) {
            batches = batchRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();
        } else if (user != null) {
            batches = batchRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(user.getId());
        } else {
            batches = batchRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();
        }

        return batches.stream().map(IngestionBatchResponse::from).toList();
    }

    @Transactional
    public void deleteBatch(Long batchId, User user) {
        IngestionBatch batch = batchRepository.findByIdAndDeletedAtIsNull(batchId)
                .orElseThrow(() -> new ResourceNotFoundException("Dataset batch not found with id: " + batchId));

        if (!isAdmin(user) && (batch.getUser() != null && batch.getUser().getId() != user.getId())) {
            throw new BadRequestException("You do not have permission to delete this dataset batch");
        }

        List<ServiceMetric> metrics = metricRepository.findByIngestionBatchId(batchId);
        List<Long> metricIds = metrics.stream().map(ServiceMetric::getId).toList();

        if (!metricIds.isEmpty()) {
            // 1. Delete associated AnomalyEvents first
            try {
                anomalyEventRepository.deleteByMetricIdIn(metricIds);
            } catch (Exception ex) {
                log.warn("Failed to clean up anomaly events for batch {}: {}", batchId, ex.getMessage());
            }

            // 2. Unlink any Incidents whose alerts were generated during this batch
            try {
                List<AlertEvent> alerts = alertEventRepository.findAll();
                List<Long> batchAlertIds = new ArrayList<>();
                for (AlertEvent a : alerts) {
                    for (ServiceMetric m : metrics) {
                        if (a.getService().getId().equals(m.getService().getId()) &&
                            a.getMetricName().equalsIgnoreCase(m.getMetricName()) &&
                            Math.abs(java.time.Duration.between(a.getTriggeredAt(), m.getRecordedAt()).getSeconds()) < 5) {
                            batchAlertIds.add(a.getId());
                        }
                    }
                }
                if (!batchAlertIds.isEmpty()) {
                    List<Incident> linkedIncidents = incidentRepository.findByAlertIdIn(batchAlertIds);
                    for (Incident inc : linkedIncidents) {
                        inc.setAlert(null);
                        incidentRepository.save(inc);
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to cleanly unlink incidents for batch {}: {}", batchId, ex.getMessage());
            }

            // 3. Delete metrics
            metricRepository.deleteByIngestionBatchId(batchId);
        }

        batch.setDeletedAt(Instant.now());
        batch.setStatus("DELETED");
        batchRepository.save(batch);

        AuditLog audit = new AuditLog(
                "DATASET_BATCH_DELETION",
                user != null ? user.getEmail() : "system",
                "Deleted ingestion batch #" + batchId + " (" + batch.getFilename() + ") containing " +
                        metrics.size() + " records. Historical incidents preserved."
        );
        auditLogRepository.save(audit);
    }

    public String getTemplateCsv() {
        return "service_name,metric_name,metric_value,unit,timestamp,environment,scenario_label,ground_truth_label\n" +
               "Payment-Service,CPU_USAGE,45.2,PERCENT,2026-11-08T10:00:00Z,PRODUCTION,NORMAL,NORMAL\n" +
               "Order-Processing-Service,MEMORY_USAGE,68.4,PERCENT,2026-11-08T10:00:00Z,PRODUCTION,NORMAL,NORMAL\n" +
               "Database-Gateway,RESPONSE_TIME,112.5,MILLISECONDS,2026-11-08T10:00:00Z,PRODUCTION,NORMAL,NORMAL\n" +
               "Auth-Identity-Service,ERROR_RATE,0.2,PERCENT,2026-11-08T10:00:00Z,PRODUCTION,NORMAL,NORMAL\n" +
               "Payment-Service,CPU_USAGE,94.8,PERCENT,2026-11-08T10:05:00Z,PRODUCTION,CPU_STARVATION,ANOMALY\n" +
               "Order-Processing-Service,RESPONSE_TIME,420.0,MILLISECONDS,2026-11-08T10:05:00Z,PRODUCTION,DATABASE_CONTENTION,ANOMALY\n";
    }
}
