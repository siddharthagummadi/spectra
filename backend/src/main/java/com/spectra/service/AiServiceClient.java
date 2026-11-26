package com.spectra.service;

import java.time.Duration;
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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Resilient client providing secure HTTP communication between the Spring Boot
 * backend and the Python analytical microservice.
 *
 * Hardened with:
 * - Explicit configurable connection and read timeouts.
 * - Thread-safe circuit breaker preventing cascading thread-pool exhaustion.
 * - Comprehensive failure handling (timeout, refusal, 4xx, 5xx, empty body).
 * - Deterministic, graceful fallback preserving 100% of API contracts.
 * - Zero uncontrolled retries to prevent request amplification.
 */
@Service
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    private final RestTemplate restTemplate;
    private final String aiServiceUrl;
    private final AiServiceCircuitBreaker circuitBreaker;

    @Autowired
    public AiServiceClient(
            @Value("${spectra.ai-service.url:http://localhost:8081}") String aiServiceUrl,
            @Value("${spectra.ai-service.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${spectra.ai-service.read-timeout-ms:5000}") int readTimeoutMs,
            @Value("${spectra.ai-service.circuit-breaker.failure-threshold:3}") int failureThreshold,
            @Value("${spectra.ai-service.circuit-breaker.reset-timeout-ms:15000}") long resetTimeoutMs) {
        this(
                createConfiguredRestTemplate(connectTimeoutMs, readTimeoutMs),
                aiServiceUrl,
                new AiServiceCircuitBreaker(failureThreshold, resetTimeoutMs)
        );
    }

    public AiServiceClient(RestTemplate restTemplate, String aiServiceUrl) {
        this(restTemplate, aiServiceUrl, new AiServiceCircuitBreaker(3, 15000L));
    }

    public AiServiceClient(RestTemplate restTemplate, String aiServiceUrl, AiServiceCircuitBreaker circuitBreaker) {
        this.restTemplate = restTemplate != null ? restTemplate : createConfiguredRestTemplate(2000, 5000);
        this.aiServiceUrl = aiServiceUrl != null ? aiServiceUrl.replaceAll("/+$", "") : "http://localhost:8081";
        this.circuitBreaker = circuitBreaker != null ? circuitBreaker : new AiServiceCircuitBreaker(3, 15000L);
    }

    public static RestTemplate createConfiguredRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.max(100, connectTimeoutMs)));
        factory.setReadTimeout(Duration.ofMillis(Math.max(100, readTimeoutMs)));
        return new RestTemplate(factory);
    }

    public AiServiceCircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    public String getAiServiceUrl() {
        return aiServiceUrl;
    }

    public RestTemplate getRestTemplate() {
        return restTemplate;
    }

    public Map<String, Object> checkHealth() {
        if (!circuitBreaker.allowExecution()) {
            log.warn("AI Service circuit breaker is OPEN ({} failures). Fast-failing health check.",
                    circuitBreaker.getConsecutiveFailures());
            return createHealthFallback();
        }

        try {
            Map response = restTemplate.getForObject(aiServiceUrl + "/health", Map.class);
            if (response == null || response.isEmpty()) {
                log.warn("AI Service returned empty response for health check");
                circuitBreaker.recordFailure();
                return createHealthFallback();
            }
            circuitBreaker.recordSuccess();
            return response;
        } catch (ResourceAccessException ex) {
            circuitBreaker.recordFailure();
            log.warn("AI Service health check failed (I/O or timeout): {}", ex.getMessage());
            return createHealthFallback();
        } catch (RestClientException ex) {
            circuitBreaker.recordFailure();
            log.warn("AI Service health check failed (HTTP or client error): {}", ex.getMessage());
            return createHealthFallback();
        } catch (Exception ex) {
            circuitBreaker.recordFailure();
            log.warn("AI Service health check unexpected error: {}", ex.getMessage());
            return createHealthFallback();
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

        if (!circuitBreaker.allowExecution()) {
            log.warn("AI Service circuit breaker is OPEN ({} failures). Fast-failing RCA request.",
                    circuitBreaker.getConsecutiveFailures());
            return createRcaFallback();
        }

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

            if (response.getBody() == null || response.getBody().isEmpty()) {
                log.warn("AI Service returned empty body for RCA analysis");
                circuitBreaker.recordFailure();
                return createRcaFallback();
            }

            circuitBreaker.recordSuccess();
            return response.getBody();
        } catch (ResourceAccessException ex) {
            circuitBreaker.recordFailure();
            log.error("AI Service RCA invocation failed (timeout or connection refused): {}", ex.getMessage());
            return createRcaFallback();
        } catch (HttpServerErrorException ex) {
            circuitBreaker.recordFailure();
            log.error("AI Service RCA invocation failed with HTTP 5xx (status {}): {}", ex.getStatusCode(), ex.getMessage());
            return createRcaFallback();
        } catch (HttpClientErrorException ex) {
            circuitBreaker.recordFailure();
            log.error("AI Service RCA invocation failed with HTTP 4xx (status {}): {}", ex.getStatusCode(), ex.getMessage());
            return createRcaFallback();
        } catch (Exception ex) {
            circuitBreaker.recordFailure();
            log.error("AI Service RCA unexpected error: {}", ex.getMessage());
            return createRcaFallback();
        }
    }

    public Map<String, Object> forecastTrajectory(
            String metricName,
            String unit,
            List<Double> values,
            Double criticalThreshold) {

        if (!circuitBreaker.allowExecution()) {
            log.warn("AI Service circuit breaker is OPEN ({} failures). Fast-failing Forecast request.",
                    circuitBreaker.getConsecutiveFailures());
            return createForecastFallback();
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("metric_name", metricName);
            payload.put("unit", unit);
            payload.put("values", values != null ? values : Collections.emptyList());
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

            if (response.getBody() == null || response.getBody().isEmpty()) {
                log.warn("AI Service returned empty body for Forecast request");
                circuitBreaker.recordFailure();
                return createForecastFallback();
            }

            circuitBreaker.recordSuccess();
            return response.getBody();
        } catch (ResourceAccessException ex) {
            circuitBreaker.recordFailure();
            log.error("AI Service Forecast failed (timeout or connection refused): {}", ex.getMessage());
            return createForecastFallback();
        } catch (RestClientException ex) {
            circuitBreaker.recordFailure();
            log.error("AI Service Forecast failed with REST error: {}", ex.getMessage());
            return createForecastFallback();
        } catch (Exception ex) {
            circuitBreaker.recordFailure();
            log.error("AI Service Forecast unexpected error: {}", ex.getMessage());
            return createForecastFallback();
        }
    }

    public Map<String, Object> queryRunbooks(String query, int topK, String metricFilter) {
        if (!circuitBreaker.allowExecution()) {
            log.warn("AI Service circuit breaker is OPEN ({} failures). Fast-failing Runbooks request.",
                    circuitBreaker.getConsecutiveFailures());
            return createQueryRunbooksFallback(query);
        }

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

            if (response.getBody() == null || response.getBody().isEmpty()) {
                log.warn("AI Service returned empty body for Runbooks query");
                circuitBreaker.recordFailure();
                return createQueryRunbooksFallback(query);
            }

            circuitBreaker.recordSuccess();
            return response.getBody();
        } catch (ResourceAccessException ex) {
            circuitBreaker.recordFailure();
            log.error("AI Service Runbooks query failed (timeout or connection refused): {}", ex.getMessage());
            return createQueryRunbooksFallback(query);
        } catch (RestClientException ex) {
            circuitBreaker.recordFailure();
            log.error("AI Service Runbooks query failed with REST error: {}", ex.getMessage());
            return createQueryRunbooksFallback(query);
        } catch (Exception ex) {
            circuitBreaker.recordFailure();
            log.error("AI Service Runbooks unexpected error: {}", ex.getMessage());
            return createQueryRunbooksFallback(query);
        }
    }

    public Map<String, Object> chatCopilot(String message, Map<String, Object> operationalContext) {
        if (!circuitBreaker.allowExecution()) {
            log.warn("AI Service circuit breaker is OPEN ({} failures). Fast-failing Copilot chat request.",
                    circuitBreaker.getConsecutiveFailures());
            return createCopilotFallback();
        }

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

            if (response.getBody() == null || response.getBody().isEmpty()) {
                log.warn("AI Service returned empty body for Copilot chat");
                circuitBreaker.recordFailure();
                return createCopilotFallback();
            }

            circuitBreaker.recordSuccess();
            return response.getBody();
        } catch (ResourceAccessException ex) {
            circuitBreaker.recordFailure();
            log.error("AI Service Copilot failed (timeout or connection refused): {}", ex.getMessage());
            return createCopilotFallback();
        } catch (RestClientException ex) {
            circuitBreaker.recordFailure();
            log.error("AI Service Copilot failed with REST error: {}", ex.getMessage());
            return createCopilotFallback();
        } catch (Exception ex) {
            circuitBreaker.recordFailure();
            log.error("AI Service Copilot unexpected error: {}", ex.getMessage());
            return createCopilotFallback();
        }
    }

    public Map<String, Object> getCopilotConfig() {
        if (!circuitBreaker.allowExecution()) {
            log.warn("AI Service circuit breaker is OPEN. Fast-failing Copilot config GET request.");
            return createCopilotConfigGetFallback();
        }

        try {
            Map response = restTemplate.getForObject(aiServiceUrl + "/api/v1/copilot/config", Map.class);
            if (response == null || response.isEmpty()) {
                circuitBreaker.recordFailure();
                return createCopilotConfigGetFallback();
            }
            circuitBreaker.recordSuccess();
            return response;
        } catch (Exception ex) {
            circuitBreaker.recordFailure();
            log.warn("Failed to fetch copilot config: {}", ex.getMessage());
            return createCopilotConfigGetFallback();
        }
    }

    public Map<String, Object> updateCopilotConfig(Map<String, Object> payload) {
        if (!circuitBreaker.allowExecution()) {
            log.warn("AI Service circuit breaker is OPEN. Fast-failing Copilot config POST request.");
            return createCopilotConfigUpdateFallback();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    aiServiceUrl + "/api/v1/copilot/config",
                    request,
                    Map.class
            );

            if (response.getBody() == null || response.getBody().isEmpty()) {
                circuitBreaker.recordFailure();
                return createCopilotConfigUpdateFallback();
            }

            circuitBreaker.recordSuccess();
            return response.getBody();
        } catch (Exception ex) {
            circuitBreaker.recordFailure();
            log.error("Failed to update copilot config: {}", ex.getMessage());
            return createCopilotConfigUpdateFallback();
        }
    }

    // --- Deterministic Fallback Factory Methods ---

    private Map<String, Object> createHealthFallback() {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("status", "UNAVAILABLE");
        fallback.put("message", "AI Microservice unreachable at " + aiServiceUrl);
        return fallback;
    }

    private Map<String, Object> createRcaFallback() {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("primary_root_cause", "Deterministic Fallback: Anomaly in service metrics");
        fallback.put("confidence_score", 0.60);
        fallback.put("failure_category", "GENERAL_ANOMALY");
        fallback.put("primary_evidence", "AI Service offline; fallback rule triggered");
        fallback.put("ranked_causes", Collections.emptyList());
        fallback.put("contributing_factors", List.of("Automated fallback response"));
        return fallback;
    }

    private Map<String, Object> createForecastFallback() {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("status", "FALLBACK");
        fallback.put("trend_direction", "UNKNOWN");
        fallback.put("outage_risk_probability", 0.10);
        fallback.put("proactive_recommendation", "Prediction service unavailable.");
        return fallback;
    }

    private Map<String, Object> createQueryRunbooksFallback(String query) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("query", query);
        fallback.put("results_count", 0);
        fallback.put("matches", Collections.emptyList());
        return fallback;
    }

    private Map<String, Object> createCopilotFallback() {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("reply", "Spectra AI Copilot is currently running in local fallback mode. Backend is operational.");
        fallback.put("engine_used", "SPRING_BOOT_FALLBACK");
        fallback.put("referenced_runbooks", Collections.emptyList());
        fallback.put("status", "SUCCESS");
        return fallback;
    }

    private Map<String, Object> createCopilotConfigGetFallback() {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("provider", "LOCAL_SRE");
        fallback.put("is_configured", false);
        fallback.put("active_model", "spectra-sre-engine");
        fallback.put("free_of_cost", true);
        fallback.put("gemini_configured", false);
        fallback.put("grok_configured", false);
        fallback.put("note", "AI Microservice unreachable. Local fallback active.");
        return fallback;
    }

    private Map<String, Object> createCopilotConfigUpdateFallback() {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("provider", "LOCAL_SRE");
        fallback.put("is_configured", false);
        fallback.put("active_model", "spectra-sre-engine");
        fallback.put("free_of_cost", true);
        fallback.put("gemini_configured", false);
        fallback.put("grok_configured", false);
        fallback.put("note", "Update failed: AI Microservice unreachable.");
        return fallback;
    }
}
