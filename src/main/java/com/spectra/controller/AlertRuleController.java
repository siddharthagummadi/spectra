package com.spectra.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spectra.dto.AlertRuleResponse;
import com.spectra.dto.CreateAlertRuleRequest;
import com.spectra.dto.UpdateAlertRuleRequest;
import com.spectra.entity.AlertRule;
import com.spectra.service.AlertService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/alert-rules")
@Validated
public class AlertRuleController {

    private final AlertService alertService;

    public AlertRuleController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping
    public ResponseEntity<AlertRuleResponse> createAlertRule(@Valid @RequestBody CreateAlertRuleRequest request) {
        AlertRule rule = alertService.createAlertRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(AlertRuleResponse.from(rule));
    }

    @GetMapping
    public ResponseEntity<List<AlertRuleResponse>> getAllRules() {
        List<AlertRuleResponse> rules = alertService.getAllRules()
                .stream()
                .map(AlertRuleResponse::from)
                .toList();
        return ResponseEntity.ok(rules);
    }

    @GetMapping("/service/{serviceId}")
    public ResponseEntity<List<AlertRuleResponse>> getRulesByServiceId(@PathVariable Long serviceId) {
        List<AlertRuleResponse> rules = alertService.getRulesByServiceId(serviceId)
                .stream()
                .map(AlertRuleResponse::from)
                .toList();
        return ResponseEntity.ok(rules);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertRuleResponse> getRuleById(@PathVariable Long id) {
        AlertRule rule = alertService.getRuleById(id);
        return ResponseEntity.ok(AlertRuleResponse.from(rule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlertRuleResponse> updateAlertRule(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAlertRuleRequest request) {
        AlertRule updatedRule = alertService.updateAlertRule(id, request);
        return ResponseEntity.ok(AlertRuleResponse.from(updatedRule));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AlertRuleResponse> updateAlertRuleStatus(
            @PathVariable Long id,
            @RequestParam boolean active) {
        AlertRule updatedRule = alertService.updateAlertRuleStatus(id, active);
        return ResponseEntity.ok(AlertRuleResponse.from(updatedRule));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlertRule(@PathVariable Long id) {
        alertService.deleteAlertRule(id);
        return ResponseEntity.noContent().build();
    }
}
