package com.spectra.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.spectra.dto.CreateMetricRequest;
import com.spectra.dto.ExternalTelemetryBatchRequest;
import com.spectra.dto.ExternalTelemetryIngestionResponse;
import com.spectra.dto.ExternalTelemetryRequest;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.MonitoredServiceRepository;

@Service
public class ExternalTelemetryService {

    private static final Logger log = LoggerFactory.getLogger(ExternalTelemetryService.class);

    private final ServiceMetricService serviceMetricService;
    private final MonitoredServiceRepository serviceRepository;
    private final ObjectMapper objectMapper;

    // Pattern for Prometheus line format: metric_name{labels} value timestamp
    // e.g.: process_cpu_usage{service="payment-service",service_id="1"} 42.5 1700000000000
    private static final Pattern PROMETHEUS_LINE_PATTERN = Pattern.compile(
            "^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\\{([^}]*)})?\\s+([+-]?[0-9]*\\.?[0-9]+(?:[eE][+-]?[0-9]+)?)(?:\\s+(\\d+))?$"
    );

    private static final Pattern LABEL_PATTERN = Pattern.compile(
            "([a-zA-Z_:][a-zA-Z0-9_:]*)\\s*=\\s*\"([^\"]*)\""
    );

    public ExternalTelemetryService(
            ServiceMetricService serviceMetricService,
            MonitoredServiceRepository serviceRepository,
            ObjectMapper objectMapper) {
        this.serviceMetricService = serviceMetricService;
        this.serviceRepository = serviceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Ingest a single external metric request.
     */
    @Transactional
    public ExternalTelemetryIngestionResponse ingestMetric(ExternalTelemetryRequest request, User user) {
        if (request == null) {
            throw new BadRequestException("External telemetry request cannot be null");
        }

        MonitoredService service = resolveService(request.getServiceId(), request.getServiceName(), user);

        CreateMetricRequest metricRequest = new CreateMetricRequest();
        metricRequest.setServiceId(service.getId());
        metricRequest.setMetricName(normalizeMetricName(request.getMetricName()));
        metricRequest.setMetricValue(request.getMetricValue());
        metricRequest.setUnit(resolveUnit(metricRequest.getMetricName(), request.getUnit()));
        metricRequest.setRecordedAt(request.getRecordedAt() != null ? request.getRecordedAt() : Instant.now());

        serviceMetricService.submitMetric(metricRequest);

        return new ExternalTelemetryIngestionResponse(
                "SUCCESS", 1, 1, 0, service.getId(), service.getName()
        );
    }

    /**
     * Ingest a batch of external telemetry observations.
     */
    public ExternalTelemetryIngestionResponse ingestBatch(ExternalTelemetryBatchRequest batchRequest, User user) {
        if (batchRequest == null) {
            throw new BadRequestException("External telemetry batch request cannot be null");
        }

        MonitoredService service = resolveService(batchRequest.getServiceId(), batchRequest.getServiceName(), user);

        List<ExternalTelemetryBatchRequest.ObservationDto> metrics = batchRequest.getMetrics();
        if (metrics == null || metrics.isEmpty()) {
            throw new BadRequestException("Batch contains no telemetry observations");
        }

        int accepted = 0;
        int rejected = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < metrics.size(); i++) {
            ExternalTelemetryBatchRequest.ObservationDto obs = metrics.get(i);
            try {
                if (obs == null || obs.getMetricName() == null || obs.getMetricValue() == null) {
                    rejected++;
                    errors.add("Observation #" + i + ": Missing metric name or value");
                    continue;
                }

                CreateMetricRequest req = new CreateMetricRequest();
                req.setServiceId(service.getId());
                req.setMetricName(normalizeMetricName(obs.getMetricName()));
                req.setMetricValue(obs.getMetricValue());
                req.setUnit(resolveUnit(req.getMetricName(), obs.getUnit()));
                req.setRecordedAt(obs.getTimestamp() != null ? obs.getTimestamp() : Instant.now());

                serviceMetricService.submitMetric(req);
                accepted++;
            } catch (Exception ex) {
                rejected++;
                errors.add("Observation #" + i + " (" + (obs != null ? obs.getMetricName() : "null") + "): " + ex.getMessage());
            }
        }

        String status = rejected == 0 ? "SUCCESS" : (accepted > 0 ? "PARTIAL_SUCCESS" : "FAILED");
        ExternalTelemetryIngestionResponse resp = new ExternalTelemetryIngestionResponse(
                status, metrics.size(), accepted, rejected, service.getId(), service.getName()
        );
        resp.setErrors(errors);
        return resp;
    }

    /**
     * Ingest OpenTelemetry standard OTLP JSON payload.
     */
    public ExternalTelemetryIngestionResponse ingestOpenTelemetry(String otlpJson, User user) {
        if (otlpJson == null || otlpJson.trim().isEmpty()) {
            throw new BadRequestException("OpenTelemetry JSON payload cannot be empty");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(otlpJson);
        } catch (Exception e) {
            throw new BadRequestException("Malformed JSON in OpenTelemetry payload: " + e.getMessage());
        }

        JsonNode resourceMetrics = root.path("resourceMetrics");
        if (!resourceMetrics.isArray() || resourceMetrics.isEmpty()) {
            throw new BadRequestException("OpenTelemetry payload must contain a non-empty 'resourceMetrics' array");
        }

        int totalReceived = 0;
        int accepted = 0;
        int rejected = 0;
        List<String> errors = new ArrayList<>();
        Long lastServiceId = null;
        String lastServiceName = null;

        for (JsonNode resMetric : resourceMetrics) {
            // Extract service identification from resource attributes
            String serviceName = null;
            Long serviceId = null;

            JsonNode attributes = resMetric.path("resource").path("attributes");
            if (attributes.isArray()) {
                for (JsonNode attr : attributes) {
                    String key = attr.path("key").asText();
                    if ("service.name".equalsIgnoreCase(key)) {
                        serviceName = extractAttributeValue(attr.path("value"));
                    } else if ("service.id".equalsIgnoreCase(key)) {
                        String idStr = extractAttributeValue(attr.path("value"));
                        try {
                            if (idStr != null) serviceId = Long.parseLong(idStr);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            MonitoredService service;
            try {
                service = resolveService(serviceId, serviceName, user);
                lastServiceId = service.getId();
                lastServiceName = service.getName();
            } catch (Exception e) {
                rejected++;
                errors.add("Failed to resolve monitored service for resource: " + e.getMessage());
                continue;
            }

            JsonNode scopeMetrics = resMetric.path("scopeMetrics");
            if (scopeMetrics.isArray()) {
                for (JsonNode scope : scopeMetrics) {
                    JsonNode metrics = scope.path("metrics");
                    if (metrics.isArray()) {
                        for (JsonNode metricNode : metrics) {
                            String rawMetricName = metricNode.path("name").asText(null);
                            String unit = metricNode.path("unit").asText(null);
                            if (rawMetricName == null) continue;

                            String normalizedMetricName = normalizeMetricName(rawMetricName);

                            // Extract data points from gauge or sum
                            JsonNode dataPoints = metricNode.path("gauge").path("dataPoints");
                            if (!dataPoints.isArray() || dataPoints.isEmpty()) {
                                dataPoints = metricNode.path("sum").path("dataPoints");
                            }

                            if (dataPoints.isArray()) {
                                for (JsonNode dp : dataPoints) {
                                    totalReceived++;
                                    try {
                                        Double value = null;
                                        if (dp.has("asDouble")) {
                                            value = dp.path("asDouble").asDouble();
                                        } else if (dp.has("asInt")) {
                                            value = (double) dp.path("asInt").asLong();
                                        }

                                        if (value == null) {
                                            rejected++;
                                            errors.add("DataPoint in metric " + rawMetricName + " lacks numeric value");
                                            continue;
                                        }

                                        // Scaling adjustments:
                                        // OTLP CPU utilization (0.0 - 1.0) -> percentage (0.0 - 100.0)
                                        if ("CPU_USAGE".equals(normalizedMetricName) && value <= 1.0 && ("1".equals(unit) || "ratio".equalsIgnoreCase(unit) || unit == null)) {
                                            value = value * 100.0;
                                        }
                                        // OTLP duration in seconds ('s') -> milliseconds
                                        if ("RESPONSE_TIME".equals(normalizedMetricName) && ("s".equalsIgnoreCase(unit) || "seconds".equalsIgnoreCase(unit))) {
                                            value = value * 1000.0;
                                        }

                                        Instant recordedAt = Instant.now();
                                        if (dp.has("timeUnixNano")) {
                                            long nanos = dp.path("timeUnixNano").asLong();
                                            if (nanos > 0) {
                                                recordedAt = Instant.ofEpochSecond(nanos / 1_000_000_000L, nanos % 1_000_000_000L);
                                            }
                                        }

                                        CreateMetricRequest req = new CreateMetricRequest();
                                        req.setServiceId(service.getId());
                                        req.setMetricName(normalizedMetricName);
                                        req.setMetricValue(value);
                                        req.setUnit(resolveUnit(normalizedMetricName, unit));
                                        req.setRecordedAt(recordedAt);

                                        serviceMetricService.submitMetric(req);
                                        accepted++;
                                    } catch (Exception ex) {
                                        rejected++;
                                        errors.add("Metric '" + rawMetricName + "' error: " + ex.getMessage());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        String status = rejected == 0 ? "SUCCESS" : (accepted > 0 ? "PARTIAL_SUCCESS" : "FAILED");
        ExternalTelemetryIngestionResponse resp = new ExternalTelemetryIngestionResponse(
                status, totalReceived, accepted, rejected, lastServiceId, lastServiceName
        );
        resp.setErrors(errors);
        return resp;
    }

    /**
     * Ingest Prometheus standard text exposition format payload.
     */
    public ExternalTelemetryIngestionResponse ingestPrometheus(String prometheusText, User user) {
        if (prometheusText == null || prometheusText.trim().isEmpty()) {
            throw new BadRequestException("Prometheus text payload cannot be empty");
        }

        String[] lines = prometheusText.split("\\r?\\n");
        int totalReceived = 0;
        int accepted = 0;
        int rejected = 0;
        List<String> errors = new ArrayList<>();
        Long lastServiceId = null;
        String lastServiceName = null;

        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue; // Skip comments and empty lines
            }

            Matcher matcher = PROMETHEUS_LINE_PATTERN.matcher(line);
            if (!matcher.matches()) {
                continue;
            }

            totalReceived++;
            String rawMetricName = matcher.group(1);
            String labelsStr = matcher.group(2);
            String valueStr = matcher.group(3);
            String timestampStr = matcher.group(4);

            try {
                double value = Double.parseDouble(valueStr);
                String serviceName = null;
                Long serviceId = null;

                if (labelsStr != null && !labelsStr.trim().isEmpty()) {
                    Matcher labelMatcher = LABEL_PATTERN.matcher(labelsStr);
                    while (labelMatcher.find()) {
                        String key = labelMatcher.group(1);
                        String val = labelMatcher.group(2);
                        if ("service".equalsIgnoreCase(key) || "service_name".equalsIgnoreCase(key) || "app".equalsIgnoreCase(key)) {
                            serviceName = val;
                        } else if ("service_id".equalsIgnoreCase(key)) {
                            try {
                                serviceId = Long.parseLong(val);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }

                MonitoredService service = resolveService(serviceId, serviceName, user);
                lastServiceId = service.getId();
                lastServiceName = service.getName();

                String normalizedMetricName = normalizeMetricName(rawMetricName);

                Instant recordedAt = Instant.now();
                if (timestampStr != null && !timestampStr.trim().isEmpty()) {
                    long ts = Long.parseLong(timestampStr);
                    if (ts > 10_000_000_000L) { // Milliseconds epoch
                        recordedAt = Instant.ofEpochMilli(ts);
                    } else if (ts > 0) { // Seconds epoch
                        recordedAt = Instant.ofEpochSecond(ts);
                    }
                }

                CreateMetricRequest req = new CreateMetricRequest();
                req.setServiceId(service.getId());
                req.setMetricName(normalizedMetricName);
                req.setMetricValue(value);
                req.setUnit(resolveUnit(normalizedMetricName, null));
                req.setRecordedAt(recordedAt);

                serviceMetricService.submitMetric(req);
                accepted++;
            } catch (Exception ex) {
                rejected++;
                errors.add("Line " + (lineNum + 1) + " ('" + rawMetricName + "'): " + ex.getMessage());
            }
        }

        String status = rejected == 0 ? "SUCCESS" : (accepted > 0 ? "PARTIAL_SUCCESS" : "FAILED");
        ExternalTelemetryIngestionResponse resp = new ExternalTelemetryIngestionResponse(
                status, totalReceived, accepted, rejected, lastServiceId, lastServiceName
        );
        resp.setErrors(errors);
        return resp;
    }

    /**
     * Resolves the target MonitoredService with multi-tenant access check.
     */
    private MonitoredService resolveService(Long serviceId, String serviceName, User user) {
        if (user != null && user.getRole() == Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Platform administrators cannot ingest telemetry");
        }

        MonitoredService service = null;

        if (serviceId != null) {
            service = serviceRepository.findById(serviceId).orElse(null);
        }

        if (service == null && serviceName != null && !serviceName.trim().isEmpty()) {
            if (user != null && user.getEnterprise() != null) {
                service = serviceRepository.findByEnterpriseIdAndNameIgnoreCase(user.getEnterprise().getId(), serviceName.trim()).orElse(null);
            }
            if (service == null) {
                service = serviceRepository.findByNameIgnoreCase(serviceName.trim()).orElse(null);
            }
        }

        if (service == null) {
            String identifier = serviceId != null ? "id=" + serviceId : "name='" + serviceName + "'";
            throw new ResourceNotFoundException("Monitored service not found for " + identifier);
        }

        if (user != null) {
            if (user.getEnterprise() != null && service.getEnterprise() != null) {
                if (!service.getEnterprise().getId().equals(user.getEnterprise().getId())) {
                    throw new ResourceNotFoundException("Monitored service not found with id: " + service.getId());
                }
            } else if (service.getUser() != null && service.getUser().getId() != user.getId()) {
                throw new ResourceNotFoundException("Monitored service not found with id: " + service.getId());
            }
        }

        return service;
    }

    /**
     * Maps diverse metric naming conventions to Spectra standard metric names:
     * CPU_USAGE, MEMORY_USAGE, RESPONSE_TIME, ERROR_RATE.
     */
    private String normalizeMetricName(String raw) {
        if (raw == null) {
            throw new BadRequestException("Metric name cannot be null");
        }
        String s = raw.trim().toLowerCase();

        // CPU metrics
        if (s.contains("cpu") && (s.contains("util") || s.contains("usage") || s.contains("pct") || s.contains("percent"))) {
            return "CPU_USAGE";
        }
        if (s.equals("cpu") || s.equals("cpu_usage") || s.equals("process.cpu.utilization") || s.equals("system.cpu.utilization")) {
            return "CPU_USAGE";
        }

        // Memory metrics
        if (s.contains("mem") && (s.contains("used") || s.contains("usage") || s.contains("util") || s.contains("percent") || s.contains("pct"))) {
            return "MEMORY_USAGE";
        }
        if (s.equals("memory") || s.equals("memory_usage") || s.equals("jvm.memory.used") || s.equals("process.runtime.jvm.memory.usage")) {
            return "MEMORY_USAGE";
        }

        // Response time / Latency metrics
        if (s.contains("duration") || s.contains("latency") || s.contains("response_time") || s.contains("responsetime") || s.contains("rt")) {
            return "RESPONSE_TIME";
        }
        if (s.equals("response_time") || s.equals("http.server.duration") || s.equals("http_requests_duration_milliseconds")) {
            return "RESPONSE_TIME";
        }

        // Error rate metrics
        if (s.contains("error") && (s.contains("rate") || s.contains("pct") || s.contains("percent") || s.contains("fail"))) {
            return "ERROR_RATE";
        }
        if (s.equals("error_rate") || s.equals("http.server.error_rate") || s.equals("http_server_error_rate")) {
            return "ERROR_RATE";
        }

        // If already uppercase standard or other
        return raw.trim().toUpperCase();
    }

    private String resolveUnit(String normalizedMetricName, String rawUnit) {
        if ("RESPONSE_TIME".equals(normalizedMetricName)) {
            return "MILLISECONDS";
        }
        if ("CPU_USAGE".equals(normalizedMetricName) || "ERROR_RATE".equals(normalizedMetricName)) {
            return "PERCENT";
        }
        if ("MEMORY_USAGE".equals(normalizedMetricName)) {
            if (rawUnit != null && (rawUnit.trim().equalsIgnoreCase("MEGABYTES") || rawUnit.trim().equalsIgnoreCase("MB"))) {
                return "MEGABYTES";
            }
            return "PERCENT";
        }
        if (rawUnit != null && !rawUnit.trim().isEmpty() && !"1".equals(rawUnit)) {
            String u = rawUnit.trim().toUpperCase();
            if (u.equals("%") || u.equals("PCT") || u.equals("PERCENTAGE")) return "PERCENT";
            if (u.equals("MS") || u.equals("MILLIS") || u.equals("MILLISECOND")) return "MILLISECONDS";
            return u;
        }

        return "COUNT";
    }

    private String extractAttributeValue(JsonNode valueNode) {
        if (valueNode == null) return null;
        if (valueNode.has("stringValue")) return valueNode.path("stringValue").asText();
        if (valueNode.has("intValue")) return valueNode.path("intValue").asText();
        if (valueNode.isTextual()) return valueNode.asText();
        if (valueNode.isNumber()) return valueNode.asText();
        return null;
    }
}
