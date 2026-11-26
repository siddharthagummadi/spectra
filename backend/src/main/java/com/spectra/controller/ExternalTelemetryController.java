package com.spectra.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spectra.dto.ExternalTelemetryBatchRequest;
import com.spectra.dto.ExternalTelemetryIngestionResponse;
import com.spectra.dto.ExternalTelemetryRequest;
import com.spectra.entity.User;
import com.spectra.repository.UserRepository;
import com.spectra.service.ExternalTelemetryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/telemetry")
@Validated
public class ExternalTelemetryController {

    private final ExternalTelemetryService telemetryService;
    private final UserRepository userRepository;

    public ExternalTelemetryController(ExternalTelemetryService telemetryService, UserRepository userRepository) {
        this.telemetryService = telemetryService;
        this.userRepository = userRepository;
    }

    private User resolveUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    /**
     * Standard JSON single metric ingestion.
     */
    @PostMapping("/external")
    public ResponseEntity<ExternalTelemetryIngestionResponse> ingestSingleMetric(
            @Valid @RequestBody ExternalTelemetryRequest request,
            Authentication auth) {
        User user = resolveUser(auth);
        ExternalTelemetryIngestionResponse response = telemetryService.ingestMetric(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Standard JSON batch metric ingestion.
     */
    @PostMapping("/batch")
    public ResponseEntity<ExternalTelemetryIngestionResponse> ingestBatchMetrics(
            @Valid @RequestBody ExternalTelemetryBatchRequest batchRequest,
            Authentication auth) {
        User user = resolveUser(auth);
        ExternalTelemetryIngestionResponse response = telemetryService.ingestBatch(batchRequest, user);
        HttpStatus status = "FAILED".equals(response.getStatus()) ? HttpStatus.BAD_REQUEST : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * OpenTelemetry standard OTLP HTTP/JSON metrics endpoint.
     */
    @PostMapping(
            value = {"/otlp/v1/metrics", "/v1/metrics"},
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ExternalTelemetryIngestionResponse> ingestOpenTelemetry(
            @RequestBody String otlpPayload,
            Authentication auth) {
        User user = resolveUser(auth);
        ExternalTelemetryIngestionResponse response = telemetryService.ingestOpenTelemetry(otlpPayload, user);
        HttpStatus status = "FAILED".equals(response.getStatus()) ? HttpStatus.BAD_REQUEST : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    /**
     * Prometheus text exposition format push endpoint.
     */
    @PostMapping(
            value = "/prometheus",
            consumes = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE, "*/*"},
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ExternalTelemetryIngestionResponse> ingestPrometheus(
            @RequestBody String prometheusPayload,
            Authentication auth) {
        User user = resolveUser(auth);
        ExternalTelemetryIngestionResponse response = telemetryService.ingestPrometheus(prometheusPayload, user);
        HttpStatus status = "FAILED".equals(response.getStatus()) ? HttpStatus.BAD_REQUEST : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }
}
