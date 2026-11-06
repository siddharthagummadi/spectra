package com.spectra.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    private final RestTemplate restTemplate;
    private final String aiServiceUrl;

    @Autowired
    public AiServiceClient(
            @Value("${spectra.ai-service.url:http://localhost:8081}") String aiServiceUrl) {
        this(new RestTemplate(), aiServiceUrl);
    }

    public AiServiceClient(RestTemplate restTemplate, String aiServiceUrl) {
        this.restTemplate = restTemplate != null ? restTemplate : new RestTemplate();
        this.aiServiceUrl = aiServiceUrl != null ? aiServiceUrl.replaceAll("/+$", "") : "http://localhost:8081";
    }

    public Map<String, Object> checkHealth() {
        try {
            return restTemplate.getForObject(aiServiceUrl + "/health", Map.class);
        } catch (Exception ex) {
            log.warn("AI Service health check failed: {}", ex.getMessage());
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("status", "UNAVAILABLE");
            fallback.put("message", "AI Microservice unreachable at " + aiServiceUrl);
            return fallback;
        }
    }

    public Map<String, Object> analyzeRootCause(
            Long serviceId,
            String serviceName,
            String incidentTitle,
            String incidentDescription,
            List<Map<String, Object>> recentMetrics,
            List<Map<String, Object>> activeAlerts,
            List<String> logs) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("service_id", serviceId != null ? serviceId : 0);
            payload.put("service_name", serviceName != null ? serviceName : "Unknown Service");
            payload.put("incident_title", incidentTitle != null ? incidentTitle : "Operational Incident");
            payload.put("incident_description", incidentDescription);
            payload.put("recent_metrics", recentMetrics != null ? recentMetrics : Collections.emptyList());
            payload.put("active_alerts", activeAlerts != null ? activeAlerts : Collections.emptyList());
            payload.put("logs", logs != null ? logs : Collections.emptyList());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aiServiceUrl + "/api/v1/rca/analyze",
                    request,
                    Map.class
            );
            return response.getBody();
        } catch (Exception ex) {
            log.error("Failed to invoke AI RCA endpoint: {}", ex.getMessage());
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("primary_root_cause", "Deterministic Fallback: Anomaly in service metrics");
            fallback.put("confidence_score", 0.60);
            fallback.put("failure_category", "GENERAL_ANOMALY");
            fallback.put("primary_evidence", "AI Service offline; fallback rule triggered");
            fallback.put("ranked_causes", Collections.emptyList());
            fallback.put("contributing_factors", List.of("Automated fallback response"));
            return fallback;
        }
    }

    public Map<String, Object> forecastTrajectory(
            String metricName,
            String unit,
            List<Double> values,
            Double criticalThreshold) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("metric_name", metricName);
            payload.put("unit", unit);
            payload.put("values", values);
            if (criticalThreshold != null) {
                payload.put("critical_threshold", criticalThreshold);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aiServiceUrl + "/api/v1/prediction/forecast",
                    request,
                    Map.class
            );
            return response.getBody();
        } catch (Exception ex) {
            log.error("Failed to invoke AI Forecast endpoint: {}", ex.getMessage());
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("status", "FALLBACK");
            fallback.put("trend_direction", "UNKNOWN");
            fallback.put("outage_risk_probability", 0.10);
            fallback.put("proactive_recommendation", "Prediction service unavailable.");
            return fallback;
        }
    }

    public Map<String, Object> queryRunbooks(String query, int topK, String metricFilter) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("query", query);
            payload.put("top_k", topK);
            if (metricFilter != null) {
                payload.put("metric_filter", metricFilter);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aiServiceUrl + "/api/v1/rag/query",
                    request,
                    Map.class
            );
            return response.getBody();
        } catch (Exception ex) {
            log.error("Failed to invoke AI RAG endpoint: {}", ex.getMessage());
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("query", query);
            fallback.put("results_count", 0);
            fallback.put("matches", Collections.emptyList());
            return fallback;
        }
    }

    public Map<String, Object> chatCopilot(String message, Map<String, Object> operationalContext) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("message", message);
            payload.put("operational_context", operationalContext != null ? operationalContext : Collections.emptyMap());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aiServiceUrl + "/api/v1/copilot/chat",
                    request,
                    Map.class
            );
            return response.getBody();
        } catch (Exception ex) {
            log.error("Failed to invoke AI Copilot endpoint: {}", ex.getMessage());
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("reply", "Spectra AI Copilot is currently running in local fallback mode. Backend is operational.");
            fallback.put("engine_used", "SPRING_BOOT_FALLBACK");
            fallback.put("referenced_runbooks", Collections.emptyList());
            fallback.put("status", "SUCCESS");
            return fallback;
        }
    }
}
