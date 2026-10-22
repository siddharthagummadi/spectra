package com.spectra.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spectra.dto.CreateMetricRequest;
import com.spectra.dto.MetricResponse;
import com.spectra.entity.ServiceMetric;
import com.spectra.service.ServiceMetricService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/metrics")
@Validated
public class ServiceMetricController {

    private final ServiceMetricService service;

    public ServiceMetricController(ServiceMetricService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MetricResponse> submitMetric(@Valid @RequestBody CreateMetricRequest request) {
        ServiceMetric createdMetric = service.submitMetric(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(MetricResponse.from(createdMetric));
    }

    @GetMapping("/service/{serviceId}")
    public ResponseEntity<List<MetricResponse>> getMetricsByServiceId(
            @PathVariable Long serviceId,
            @RequestParam(defaultValue = "50") int limit) {
        List<MetricResponse> metrics = service.getMetricsByServiceId(serviceId, limit)
                .stream()
                .map(MetricResponse::from)
                .toList();
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/recent")
    public ResponseEntity<List<MetricResponse>> getRecentMetrics(
            @RequestParam(defaultValue = "50") int limit) {
        List<MetricResponse> metrics = service.getRecentMetrics(limit)
                .stream()
                .map(MetricResponse::from)
                .toList();
        return ResponseEntity.ok(metrics);
    }
}
