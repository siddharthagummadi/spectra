package com.spectra.agent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Outbound-only HTTPS/HTTP client communicating with Spectra /api/agent/** endpoints.
 */
public class SpectraApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String serverUrl;

    public SpectraApiClient(String serverUrl) {
        this.serverUrl = serverUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public AgentModels.EnrollResponse enroll(AgentModels.EnrollRequest request) throws Exception {
        String body = mapper.writeValueAsString(request);
        HttpRequest httpReq = HttpRequest.newBuilder(URI.create(serverUrl + "/api/agent/enroll"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("Enrollment failed with HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return mapper.readValue(resp.body(), AgentModels.EnrollResponse.class);
    }

    public SendResult sendBatch(String agentCredential, AgentModels.TelemetryBatch batch) {
        try {
            String body = mapper.writeValueAsString(batch);
            HttpRequest httpReq = HttpRequest.newBuilder(URI.create(serverUrl + "/api/agent/telemetry/batch"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + agentCredential)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return new SendResult(true, false, resp.statusCode(), resp.body());
            }
            boolean retryable = resp.statusCode() >= 500 || resp.statusCode() == 429;
            return new SendResult(false, retryable, resp.statusCode(), resp.body());
        } catch (Exception ex) {
            return new SendResult(false, true, 0, ex.getMessage());
        }
    }

    public boolean sendHeartbeat(String agentCredential, AgentModels.HeartbeatRequest request) {
        try {
            String body = mapper.writeValueAsString(request);
            HttpRequest httpReq = HttpRequest.newBuilder(URI.create(serverUrl + "/api/agent/heartbeat"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + agentCredential)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() >= 200 && resp.statusCode() < 300;
        } catch (Exception ex) {
            return false;
        }
    }

    public static long computeBackoffMs(int attempt) {
        long base = 2000L * (1L << Math.min(5, Math.max(0, attempt)));
        return Math.min(60_000L, base);
    }

    public record SendResult(boolean success, boolean retryable, int statusCode, String message) {}
}
