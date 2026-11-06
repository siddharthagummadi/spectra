package com.spectra.controller;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spectra.service.AiServiceClient;
import com.spectra.service.IncidentService;
import com.spectra.service.ServiceMetricService;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiServiceClient aiServiceClient;

    public AiController(AiServiceClient aiServiceClient) {
        this.aiServiceClient = aiServiceClient;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getAiHealth() {
        return ResponseEntity.ok(aiServiceClient.checkHealth());
    }

    @PostMapping("/rca")
    public ResponseEntity<Map<String, Object>> analyzeRootCause(@RequestBody Map<String, Object> request) {
        Long serviceId = request.containsKey("serviceId") && request.get("serviceId") != null
                ? Long.valueOf(request.get("serviceId").toString())
                : null;
        String serviceName = (String) request.getOrDefault("serviceName", "Service");
        String title = (String) request.getOrDefault("incidentTitle", "Incident");
        String desc = (String) request.get("incidentDescription");
        List<Map<String, Object>> recentMetrics = (List<Map<String, Object>>) request.get("recentMetrics");
        List<Map<String, Object>> activeAlerts = (List<Map<String, Object>>) request.get("activeAlerts");
        List<String> logs = (List<String>) request.get("logs");

        Map<String, Object> result = aiServiceClient.analyzeRootCause(
                serviceId, serviceName, title, desc, recentMetrics, activeAlerts, logs
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/forecast")
    public ResponseEntity<Map<String, Object>> forecastTelemetry(@RequestBody Map<String, Object> request) {
        String metricName = (String) request.getOrDefault("metricName", "CPU_USAGE");
        String unit = (String) request.getOrDefault("unit", "PERCENT");
        List<Number> rawVals = (List<Number>) request.getOrDefault("values", Collections.emptyList());
        List<Double> values = rawVals.stream().map(Number::doubleValue).toList();
        Double threshold = request.containsKey("criticalThreshold") && request.get("criticalThreshold") != null
                ? Double.valueOf(request.get("criticalThreshold").toString())
                : null;

        Map<String, Object> result = aiServiceClient.forecastTrajectory(metricName, unit, values, threshold);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/runbooks")
    public ResponseEntity<Map<String, Object>> queryRunbooks(@RequestBody Map<String, Object> request) {
        String query = (String) request.getOrDefault("query", "");
        int topK = request.containsKey("topK") ? Integer.parseInt(request.get("topK").toString()) : 3;
        String metricFilter = (String) request.get("metricFilter");

        Map<String, Object> result = aiServiceClient.queryRunbooks(query, topK, metricFilter);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/copilot")
    public ResponseEntity<Map<String, Object>> chatCopilot(@RequestBody Map<String, Object> request) {
        String message = (String) request.getOrDefault("message", "");
        Map<String, Object> context = (Map<String, Object>) request.get("operationalContext");

        Map<String, Object> result = aiServiceClient.chatCopilot(message, context);
        return ResponseEntity.ok(result);
    }
}
