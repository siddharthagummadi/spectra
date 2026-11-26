package com.spectra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.spectra.dto.CreateAlertRuleRequest;
import com.spectra.dto.UpdateAlertRuleRequest;
import com.spectra.entity.AlertEvent;
import com.spectra.entity.AlertRule;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.ServiceMetric;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.AlertEventRepository;
import com.spectra.repository.AlertRuleRepository;
import com.spectra.repository.MonitoredServiceRepository;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRuleRepository ruleRepository;

    @Mock
    private AlertEventRepository alertEventRepository;

    @Mock
    private MonitoredServiceRepository serviceRepository;

    @InjectMocks
    private AlertService alertService;

    @Test
    void createAlertRule_Success() {
        MonitoredService service = new MonitoredService();
        service.setId(1L);
        service.setName("Auth Service");

        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        request.setServiceId(1L);
        request.setRuleName("High CPU Alert");
        request.setMetricName("CPU_USAGE");
        request.setComparator("GREATER_THAN");
        request.setThresholdValue(85.0);
        request.setSeverity("HIGH");

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(ruleRepository.existsByServiceIdAndRuleNameIgnoreCase(1L, "High CPU Alert")).thenReturn(false);
        when(ruleRepository.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRule created = alertService.createAlertRule(request);

        assertNotNull(created);
        assertEquals("High CPU Alert", created.getRuleName());
        assertEquals("PERCENT", created.getUnit());
        assertEquals(85.0, created.getThresholdValue());
        assertEquals("HIGH", created.getSeverity());
        assertTrue(created.isActive());
    }

    @Test
    void createAlertRule_NonexistentService_ThrowsResourceNotFoundException() {
        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        request.setServiceId(999L);
        request.setRuleName("Rule");
        request.setMetricName("CPU_USAGE");
        request.setComparator("GREATER_THAN");
        request.setThresholdValue(80.0);
        request.setSeverity("HIGH");

        when(serviceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> alertService.createAlertRule(request));
    }

    @Test
    void createAlertRule_DuplicateRuleName_ThrowsIllegalArgumentException() {
        MonitoredService service = new MonitoredService();
        service.setId(1L);

        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        request.setServiceId(1L);
        request.setRuleName("Duplicate Rule");
        request.setMetricName("CPU_USAGE");
        request.setComparator("GREATER_THAN");
        request.setThresholdValue(80.0);
        request.setSeverity("HIGH");

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(ruleRepository.existsByServiceIdAndRuleNameIgnoreCase(1L, "Duplicate Rule")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> alertService.createAlertRule(request));
    }

    @Test
    void createAlertRule_InvalidComparator_ThrowsBadRequestException() {
        MonitoredService service = new MonitoredService();
        service.setId(1L);

        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        request.setServiceId(1L);
        request.setRuleName("Rule");
        request.setMetricName("CPU_USAGE");
        request.setComparator("EQUALS");
        request.setThresholdValue(80.0);
        request.setSeverity("HIGH");

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));

        assertThrows(BadRequestException.class, () -> alertService.createAlertRule(request));
    }

    @Test
    void createAlertRule_InvalidThreshold_ThrowsBadRequestException() {
        MonitoredService service = new MonitoredService();
        service.setId(1L);

        CreateAlertRuleRequest request = new CreateAlertRuleRequest();
        request.setServiceId(1L);
        request.setRuleName("Rule");
        request.setMetricName("CPU_USAGE");
        request.setComparator("GREATER_THAN");
        request.setThresholdValue(150.0);
        request.setSeverity("HIGH");

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));

        assertThrows(BadRequestException.class, () -> alertService.createAlertRule(request));
    }

    @Test
    void evaluateMetric_ConditionBreached_CreatesTriggeredAlert() {
        MonitoredService service = new MonitoredService();
        service.setId(1L);
        service.setName("Order Service");

        AlertRule rule = new AlertRule();
        rule.setId(10L);
        rule.setService(service);
        rule.setRuleName("High Latency");
        rule.setMetricName("RESPONSE_TIME");
        rule.setUnit("MILLISECONDS");
        rule.setComparator("GREATER_THAN");
        rule.setThresholdValue(100.0);
        rule.setSeverity("HIGH");
        rule.setActive(true);

        ServiceMetric metric = new ServiceMetric();
        metric.setService(service);
        metric.setMetricName("RESPONSE_TIME");
        metric.setUnit("MILLISECONDS");
        metric.setMetricValue(150.0);
        metric.setRecordedAt(Instant.parse("2026-10-21T07:00:00Z"));

        when(ruleRepository.findByServiceIdAndMetricNameAndUnitAndActiveTrue(1L, "RESPONSE_TIME", "MILLISECONDS"))
                .thenReturn(List.of(rule));
        when(alertEventRepository.findFirstByRuleIdAndStatusIn(10L, List.of("TRIGGERED", "ACKNOWLEDGED")))
                .thenReturn(Optional.empty());

        alertService.evaluateMetric(metric);

        verify(alertEventRepository).save(any(AlertEvent.class));
    }

    @Test
    void evaluateMetric_ConditionBreachedAgain_DoesNotCreateDuplicateAlert() {
        MonitoredService service = new MonitoredService();
        service.setId(1L);

        AlertRule rule = new AlertRule();
        rule.setId(10L);
        rule.setService(service);
        rule.setMetricName("RESPONSE_TIME");
        rule.setUnit("MILLISECONDS");
        rule.setComparator("GREATER_THAN");
        rule.setThresholdValue(100.0);
        rule.setActive(true);

        ServiceMetric metric = new ServiceMetric();
        metric.setService(service);
        metric.setMetricName("RESPONSE_TIME");
        metric.setUnit("MILLISECONDS");
        metric.setMetricValue(180.0);
        metric.setRecordedAt(Instant.now());

        AlertEvent existingAlert = new AlertEvent();
        existingAlert.setId(50L);
        existingAlert.setStatus("TRIGGERED");

        when(ruleRepository.findByServiceIdAndMetricNameAndUnitAndActiveTrue(1L, "RESPONSE_TIME", "MILLISECONDS"))
                .thenReturn(List.of(rule));
        when(alertEventRepository.findFirstByRuleIdAndStatusIn(10L, List.of("TRIGGERED", "ACKNOWLEDGED")))
                .thenReturn(Optional.of(existingAlert));

        alertService.evaluateMetric(metric);

        verify(alertEventRepository, never()).save(any(AlertEvent.class));
    }

    @Test
    void evaluateMetric_ConditionRecovers_ResolvesTriggeredAlert() {
        MonitoredService service = new MonitoredService();
        service.setId(1L);

        AlertRule rule = new AlertRule();
        rule.setId(10L);
        rule.setService(service);
        rule.setMetricName("CPU_USAGE");
        rule.setUnit("PERCENT");
        rule.setComparator("GREATER_THAN");
        rule.setThresholdValue(80.0);
        rule.setActive(true);

        Instant measurementTime = Instant.parse("2026-10-21T07:15:00Z");
        ServiceMetric metric = new ServiceMetric();
        metric.setService(service);
        metric.setMetricName("CPU_USAGE");
        metric.setUnit("PERCENT");
        metric.setMetricValue(45.0);
        metric.setRecordedAt(measurementTime);

        AlertEvent existingAlert = new AlertEvent();
        existingAlert.setId(50L);
        existingAlert.setStatus("TRIGGERED");

        when(ruleRepository.findByServiceIdAndMetricNameAndUnitAndActiveTrue(1L, "CPU_USAGE", "PERCENT"))
                .thenReturn(List.of(rule));
        when(alertEventRepository.findFirstByRuleIdAndStatusIn(10L, List.of("TRIGGERED", "ACKNOWLEDGED")))
                .thenReturn(Optional.of(existingAlert));

        alertService.evaluateMetric(metric);

        assertEquals("RESOLVED", existingAlert.getStatus());
        assertEquals(measurementTime, existingAlert.getResolvedAt());
        assertEquals(45.0, existingAlert.getResolvedValue());
        verify(alertEventRepository).save(existingAlert);
    }

    @Test
    void evaluateMetric_ConditionRecovers_ResolvesAcknowledgedAlert() {
        MonitoredService service = new MonitoredService();
        service.setId(1L);

        AlertRule rule = new AlertRule();
        rule.setId(10L);
        rule.setService(service);
        rule.setMetricName("CPU_USAGE");
        rule.setUnit("PERCENT");
        rule.setComparator("GREATER_THAN");
        rule.setThresholdValue(80.0);
        rule.setActive(true);

        Instant measurementTime = Instant.parse("2026-10-21T07:20:00Z");
        ServiceMetric metric = new ServiceMetric();
        metric.setService(service);
        metric.setMetricName("CPU_USAGE");
        metric.setUnit("PERCENT");
        metric.setMetricValue(55.0);
        metric.setRecordedAt(measurementTime);

        AlertEvent acknowledgedAlert = new AlertEvent();
        acknowledgedAlert.setId(51L);
        acknowledgedAlert.setStatus("ACKNOWLEDGED");

        when(ruleRepository.findByServiceIdAndMetricNameAndUnitAndActiveTrue(1L, "CPU_USAGE", "PERCENT"))
                .thenReturn(List.of(rule));
        when(alertEventRepository.findFirstByRuleIdAndStatusIn(10L, List.of("TRIGGERED", "ACKNOWLEDGED")))
                .thenReturn(Optional.of(acknowledgedAlert));

        alertService.evaluateMetric(metric);

        assertEquals("RESOLVED", acknowledgedAlert.getStatus());
        assertEquals(measurementTime, acknowledgedAlert.getResolvedAt());
        assertEquals(55.0, acknowledgedAlert.getResolvedValue());
        verify(alertEventRepository).save(acknowledgedAlert);
    }

    @Test
    void evaluateMetric_UnitMismatch_DoesNotEvaluate() {
        MonitoredService service = new MonitoredService();
        service.setId(1L);

        ServiceMetric metric = new ServiceMetric();
        metric.setService(service);
        metric.setMetricName("MEMORY_USAGE");
        metric.setUnit("PERCENT");
        metric.setMetricValue(70.0);
        metric.setRecordedAt(Instant.now());

        when(ruleRepository.findByServiceIdAndMetricNameAndUnitAndActiveTrue(1L, "MEMORY_USAGE", "PERCENT"))
                .thenReturn(List.of());

        alertService.evaluateMetric(metric);

        verify(alertEventRepository, never()).save(any(AlertEvent.class));
    }

    @Test
    void updateAlertRule_Reconfiguration_ResolvesOpenAlerts() {
        MonitoredService service = new MonitoredService();
        service.setId(1L);

        AlertRule existing = new AlertRule();
        existing.setId(5L);
        existing.setService(service);
        existing.setRuleName("Rule");
        existing.setMetricName("CPU_USAGE");
        existing.setUnit("PERCENT");
        existing.setComparator("GREATER_THAN");
        existing.setThresholdValue(80.0);
        existing.setSeverity("HIGH");

        UpdateAlertRuleRequest request = new UpdateAlertRuleRequest();
        request.setRuleName("Rule");
        request.setMetricName("CPU_USAGE");
        request.setUnit("PERCENT");
        request.setComparator("GREATER_THAN");
        request.setThresholdValue(95.0);
        request.setSeverity("CRITICAL");

        AlertEvent activeAlert = new AlertEvent();
        activeAlert.setId(100L);
        activeAlert.setStatus("TRIGGERED");
        activeAlert.setMessage("Initial breach");

        when(ruleRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(ruleRepository.existsByServiceIdAndRuleNameIgnoreCaseAndIdNot(1L, "Rule", 5L)).thenReturn(false);
        when(alertEventRepository.findByRuleIdAndStatusIn(5L, List.of("TRIGGERED", "ACKNOWLEDGED")))
                .thenReturn(List.of(activeAlert));
        when(ruleRepository.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        alertService.updateAlertRule(5L, request);

        assertEquals("RESOLVED", activeAlert.getStatus());
        assertNotNull(activeAlert.getResolvedAt());
        verify(alertEventRepository).save(activeAlert);
    }

    @Test
    void updateAlertRuleStatus_Deactivation_ResolvesOpenAlerts() {
        MonitoredService service = new MonitoredService();
        service.setId(1L);

        AlertRule existing = new AlertRule();
        existing.setId(5L);
        existing.setService(service);
        existing.setActive(true);

        AlertEvent activeAlert = new AlertEvent();
        activeAlert.setId(101L);
        activeAlert.setStatus("TRIGGERED");
        activeAlert.setMessage("Active breach");

        when(ruleRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(alertEventRepository.findByRuleIdAndStatusIn(5L, List.of("TRIGGERED", "ACKNOWLEDGED")))
                .thenReturn(List.of(activeAlert));
        when(ruleRepository.save(any(AlertRule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRule updated = alertService.updateAlertRuleStatus(5L, false);

        assertFalse(updated.isActive());
        assertEquals("RESOLVED", activeAlert.getStatus());
        assertNotNull(activeAlert.getResolvedAt());
        verify(alertEventRepository).save(activeAlert);
    }

    @Test
    void acknowledgeAlert_Success() {
        AlertEvent alert = new AlertEvent();
        alert.setId(10L);
        alert.setStatus("TRIGGERED");

        when(alertEventRepository.findById(10L)).thenReturn(Optional.of(alert));
        when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertEvent result = alertService.acknowledgeAlert(10L);

        assertEquals("ACKNOWLEDGED", result.getStatus());
    }

    @Test
    void acknowledgeAlert_AlreadyResolved_ThrowsBadRequestException() {
        AlertEvent alert = new AlertEvent();
        alert.setId(10L);
        alert.setStatus("RESOLVED");

        when(alertEventRepository.findById(10L)).thenReturn(Optional.of(alert));

        assertThrows(BadRequestException.class, () -> alertService.acknowledgeAlert(10L));
    }

    @Test
    void deleteAlertRule_Success_WhenNoAssociatedAlerts() {
        AlertRule rule = new AlertRule();
        rule.setId(10L);

        when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));
        when(alertEventRepository.findByRuleIdAndStatusIn(10L, List.of("TRIGGERED", "ACKNOWLEDGED", "RESOLVED")))
                .thenReturn(List.of());

        alertService.deleteAlertRule(10L);

        verify(ruleRepository).delete(rule);
    }

    @Test
    void deleteAlertRule_ThrowsIllegalArgumentException_WhenAssociatedAlertsExist() {
        AlertRule rule = new AlertRule();
        rule.setId(10L);

        AlertEvent event = new AlertEvent();
        event.setId(100L);

        when(ruleRepository.findById(10L)).thenReturn(Optional.of(rule));
        when(alertEventRepository.findByRuleIdAndStatusIn(10L, List.of("TRIGGERED", "ACKNOWLEDGED", "RESOLVED")))
                .thenReturn(List.of(event));

        assertThrows(IllegalArgumentException.class, () -> alertService.deleteAlertRule(10L));
        verify(ruleRepository, never()).delete(any());
    }
}
