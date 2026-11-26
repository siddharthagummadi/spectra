package com.spectra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

class AiServiceClientResilienceTest {

    private RestTemplate restTemplate;
    private AiServiceCircuitBreaker circuitBreaker;
    private AiServiceClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        // 3 failures to trip, 200ms reset timeout for fast testing
        circuitBreaker = new AiServiceCircuitBreaker(3, 200L);
        client = new AiServiceClient(restTemplate, "http://localhost:8081", circuitBreaker);
    }

    // --- 1. Successful Responses ---

    @Test
    @DisplayName("Health Check: Successful 200 response returns service health and resets failures")
    void checkHealth_SuccessfulResponse_ReturnsHealthMap() {
        Map<String, Object> mockResp = new HashMap<>();
        mockResp.put("status", "UP");
        mockResp.put("service", "spectra-ai-service");
        when(restTemplate.getForObject("http://localhost:8081/health", Map.class)).thenReturn(mockResp);

        Map<String, Object> result = client.checkHealth();

        assertEquals("UP", result.get("status"));
        assertEquals("spectra-ai-service", result.get("service"));
        assertEquals(AiServiceCircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertEquals(0, circuitBreaker.getConsecutiveFailures());
    }

    @Test
    @DisplayName("RCA Analysis: Successful response returns root cause and confidence")
    void analyzeRootCause_SuccessfulResponse_ReturnsRcaData() {
        Map<String, Object> rcaData = Map.of(
                "primary_root_cause", "Database Connection Pool Starvation",
                "confidence_score", 0.88,
                "failure_category", "DATABASE_CONTENTION"
        );
        when(restTemplate.postForEntity(eq("http://localhost:8081/api/v1/rca/analyze"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(rcaData));

        Map<String, Object> result = client.analyzeRootCause(1L, "OrderService", "Slow checkout", null, null, null, null);

        assertEquals("Database Connection Pool Starvation", result.get("primary_root_cause"));
        assertEquals(0.88, result.get("confidence_score"));
        assertEquals("DATABASE_CONTENTION", result.get("failure_category"));
        assertEquals(AiServiceCircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    // --- 2. Connection Refused Failure ---

    @Test
    @DisplayName("Connection Refused: ResourceAccessException returns deterministic fallback and records failure")
    void connectionRefused_ReturnsFallbackAndRecordsFailure() {
        when(restTemplate.getForObject("http://localhost:8081/health", Map.class))
                .thenThrow(new ResourceAccessException("I/O error on GET request: Connection refused: connect", new ConnectException("Connection refused")));

        Map<String, Object> result = client.checkHealth();

        assertEquals("UNAVAILABLE", result.get("status"));
        assertTrue(result.get("message").toString().contains("AI Microservice unreachable"));
        assertEquals(1, circuitBreaker.getConsecutiveFailures());
        assertEquals(AiServiceCircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    // --- 3. Connection & Read Timeouts ---

    @Test
    @DisplayName("Read Timeout: SocketTimeoutException returns deterministic RCA fallback")
    void readTimeout_Rca_ReturnsFallbackAndIncrementsFailures() {
        when(restTemplate.postForEntity(eq("http://localhost:8081/api/v1/rca/analyze"), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Read timed out", new SocketTimeoutException("Read timed out")));

        Map<String, Object> result = client.analyzeRootCause(1L, "PaymentService", "Timeout", null, null, null, null);

        assertNotNull(result);
        assertEquals("Deterministic Fallback: Anomaly in service metrics", result.get("primary_root_cause"));
        assertEquals("GENERAL_ANOMALY", result.get("failure_category"));
        assertEquals(0.60, result.get("confidence_score"));
        assertEquals(1, circuitBreaker.getConsecutiveFailures());
    }

    // --- 4. HTTP 5xx Server Errors ---

    @Test
    @DisplayName("HTTP 500 Internal Server Error: Handled gracefully, returning fallback")
    void http500_Forecast_ReturnsFallback() {
        when(restTemplate.postForEntity(eq("http://localhost:8081/api/v1/prediction/forecast"), any(), eq(Map.class)))
                .thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", HttpHeaders.EMPTY, new byte[0], null));

        Map<String, Object> result = client.forecastTrajectory("CPU_USAGE", "PERCENT", List.of(50.0, 60.0), 90.0);

        assertEquals("FALLBACK", result.get("status"));
        assertEquals("UNKNOWN", result.get("trend_direction"));
        assertEquals(0.10, result.get("outage_risk_probability"));
        assertEquals(1, circuitBreaker.getConsecutiveFailures());
    }

    // --- 5. HTTP 4xx Client Errors ---

    @Test
    @DisplayName("HTTP 400 Bad Request: Handled gracefully, returning query fallback")
    void http400_Runbooks_ReturnsFallback() {
        when(restTemplate.postForEntity(eq("http://localhost:8081/api/v1/rag/query"), any(), eq(Map.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, new byte[0], null));

        Map<String, Object> result = client.queryRunbooks("Query", 3, null);

        assertEquals("Query", result.get("query"));
        assertEquals(0, result.get("results_count"));
        assertEquals(Collections.emptyList(), result.get("matches"));
        assertEquals(1, circuitBreaker.getConsecutiveFailures());
    }

    // --- 6. Empty / Invalid Response Body ---

    @Test
    @DisplayName("Empty response body: Treated as failure, returning deterministic fallback")
    void emptyResponseBody_Copilot_ReturnsFallback() {
        when(restTemplate.postForEntity(eq("http://localhost:8081/api/v1/copilot/chat"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(null));

        Map<String, Object> result = client.chatCopilot("Hello", Collections.emptyMap());

        assertEquals("SPRING_BOOT_FALLBACK", result.get("engine_used"));
        assertEquals("SUCCESS", result.get("status"));
        assertEquals(1, circuitBreaker.getConsecutiveFailures());
    }

    // --- 7. Circuit Breaker Tripping (OPEN State) & Fast-Failing ---

    @Test
    @DisplayName("Circuit Breaker: Tripping to OPEN state stops outbound HTTP calls and fast-fails")
    void circuitBreaker_TripsAfterThresholdFailures_FastFailsWithoutCallingHttp() {
        when(restTemplate.getForObject("http://localhost:8081/health", Map.class))
                .thenThrow(new ResourceAccessException("Connection refused"));

        // 1st failure
        client.checkHealth();
        assertEquals(1, circuitBreaker.getConsecutiveFailures());
        assertEquals(AiServiceCircuitBreaker.State.CLOSED, circuitBreaker.getState());

        // 2nd failure
        client.checkHealth();
        assertEquals(2, circuitBreaker.getConsecutiveFailures());
        assertEquals(AiServiceCircuitBreaker.State.CLOSED, circuitBreaker.getState());

        // 3rd failure -> Breaker trips to OPEN!
        client.checkHealth();
        assertEquals(3, circuitBreaker.getConsecutiveFailures());
        assertEquals(AiServiceCircuitBreaker.State.OPEN, circuitBreaker.getState());

        // 4th call: Circuit breaker is OPEN. It should fast-fail IMMEDIATELY without invoking restTemplate!
        Map<String, Object> fastFailResult = client.checkHealth();
        assertEquals("UNAVAILABLE", fastFailResult.get("status"));

        // Verify restTemplate was called exactly 3 times, NOT 4 times!
        verify(restTemplate, times(3)).getForObject("http://localhost:8081/health", Map.class);
    }

    // --- 8. Circuit Breaker Recovery (HALF_OPEN -> CLOSED) ---

    @Test
    @DisplayName("Circuit Breaker Recovery: Recovers from OPEN to CLOSED when service becomes available")
    void circuitBreaker_RecoversWhenServiceRestored() throws InterruptedException {
        // Force breaker to OPEN
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        circuitBreaker.recordFailure();
        assertEquals(AiServiceCircuitBreaker.State.OPEN, circuitBreaker.getState());

        // Fast-fail while window active
        Map<String, Object> blockedResult = client.checkHealth();
        assertEquals("UNAVAILABLE", blockedResult.get("status"));

        // Wait for reset timeout window (200ms)
        Thread.sleep(250);

        // Service restored: next call is trial request in HALF_OPEN state
        Map<String, Object> restoredResp = Map.of("status", "UP", "service", "spectra-ai-service");
        when(restTemplate.getForObject("http://localhost:8081/health", Map.class)).thenReturn(restoredResp);

        Map<String, Object> trialResult = client.checkHealth();

        // Successful trial request should close circuit breaker and restore normal state
        assertEquals("UP", trialResult.get("status"));
        assertEquals(AiServiceCircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertEquals(0, circuitBreaker.getConsecutiveFailures());
    }

    // --- 9. Configurable RestTemplate Construction ---

    @Test
    @DisplayName("Factory creates RestTemplate with configured connection and read timeouts")
    void createConfiguredRestTemplate_SetsTimeouts() {
        RestTemplate rt = AiServiceClient.createConfiguredRestTemplate(1500, 4000);
        assertNotNull(rt);
        assertNotNull(rt.getRequestFactory());
    }
}
