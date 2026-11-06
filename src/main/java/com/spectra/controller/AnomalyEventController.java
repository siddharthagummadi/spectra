package com.spectra.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spectra.dto.AnomalyEventResponse;
import com.spectra.entity.AnomalyEvent;
import com.spectra.service.AnomalyDetectionService;

@RestController
@RequestMapping("/api/anomalies")
@Validated
public class AnomalyEventController {

    private final AnomalyDetectionService anomalyDetectionService;

    public AnomalyEventController(AnomalyDetectionService anomalyDetectionService) {
        this.anomalyDetectionService = anomalyDetectionService;
    }

    @GetMapping("/recent")
    public ResponseEntity<List<AnomalyEventResponse>> getRecentAnomalies(
            @RequestParam(defaultValue = "50") int limit) {
        List<AnomalyEventResponse> responses = anomalyDetectionService.getRecentAnomalies(limit)
                .stream()
                .map(AnomalyEventResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/service/{serviceId}")
    public ResponseEntity<List<AnomalyEventResponse>> getAnomaliesByServiceId(
            @PathVariable Long serviceId,
            @RequestParam(defaultValue = "50") int limit) {
        List<AnomalyEventResponse> responses = anomalyDetectionService.getAnomaliesByServiceId(serviceId, limit)
                .stream()
                .map(AnomalyEventResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnomalyEventResponse> getAnomalyById(@PathVariable Long id) {
        AnomalyEvent anomaly = anomalyDetectionService.getAnomalyById(id);
        return ResponseEntity.ok(AnomalyEventResponse.from(anomaly));
    }

    @PatchMapping("/{id}/acknowledge")
    public ResponseEntity<AnomalyEventResponse> acknowledgeAnomaly(@PathVariable Long id) {
        AnomalyEvent acknowledged = anomalyDetectionService.acknowledgeAnomaly(id);
        return ResponseEntity.ok(AnomalyEventResponse.from(acknowledged));
    }
}
