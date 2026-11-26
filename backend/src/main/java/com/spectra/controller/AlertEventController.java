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

import com.spectra.dto.AlertEventResponse;
import com.spectra.entity.AlertEvent;
import com.spectra.service.AlertService;

@RestController
@RequestMapping("/api/alerts")
@Validated
public class AlertEventController {

    private final AlertService alertService;

    public AlertEventController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public ResponseEntity<List<AlertEventResponse>> getAllAlerts(
            @RequestParam(required = false) String status) {
        List<AlertEventResponse> alerts = alertService.getAllAlerts(status)
                .stream()
                .map(AlertEventResponse::from)
                .toList();
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/service/{serviceId}")
    public ResponseEntity<List<AlertEventResponse>> getAlertsByServiceId(
            @PathVariable Long serviceId,
            @RequestParam(required = false) String status) {
        List<AlertEventResponse> alerts = alertService.getAlertsByServiceId(serviceId, status)
                .stream()
                .map(AlertEventResponse::from)
                .toList();
        return ResponseEntity.ok(alerts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertEventResponse> getAlertById(@PathVariable Long id) {
        AlertEvent alert = alertService.getAlertById(id);
        return ResponseEntity.ok(AlertEventResponse.from(alert));
    }

    @PatchMapping("/{id}/acknowledge")
    public ResponseEntity<AlertEventResponse> acknowledgeAlert(@PathVariable Long id) {
        AlertEvent acknowledgedAlert = alertService.acknowledgeAlert(id);
        return ResponseEntity.ok(AlertEventResponse.from(acknowledgedAlert));
    }
}
