package com.spectra.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.spectra.entity.AlertEvent;
import com.spectra.entity.AlertRule;
import com.spectra.entity.MonitoredService;
import com.spectra.exception.GlobalExceptionHandler;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.service.AlertService;

@ExtendWith(MockitoExtension.class)
class AlertEventControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AlertService alertService;

    @InjectMocks
    private AlertEventController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllAlerts_ReturnsOk() throws Exception {
        MonitoredService service = new MonitoredService();
        service.setId(1L);
        service.setName("Order Service");

        AlertRule rule = new AlertRule();
        rule.setId(10L);
        rule.setRuleName("High Latency");

        AlertEvent event = new AlertEvent();
        event.setId(100L);
        event.setService(service);
        event.setRule(rule);
        event.setMetricName("RESPONSE_TIME");
        event.setUnit("MILLISECONDS");
        event.setTriggerValue(150.0);
        event.setThresholdValue(100.0);
        event.setSeverity("HIGH");
        event.setStatus("TRIGGERED");
        event.setMessage("Breached threshold");
        event.setTriggeredAt(Instant.parse("2026-10-21T07:00:00Z"));

        when(alertService.getAllAlerts(null)).thenReturn(List.of(event));

        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].ruleName").value("High Latency"))
                .andExpect(jsonPath("$[0].serviceName").value("Order Service"))
                .andExpect(jsonPath("$[0].status").value("TRIGGERED"));
    }

    @Test
    void getAllAlerts_WithStatusFilter_ReturnsOk() throws Exception {
        when(alertService.getAllAlerts("TRIGGERED")).thenReturn(List.of());

        mockMvc.perform(get("/api/alerts").param("status", "TRIGGERED"))
                .andExpect(status().isOk());
    }

    @Test
    void getAlertsByServiceId_ReturnsOk() throws Exception {
        when(alertService.getAlertsByServiceId(1L, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/alerts/service/1"))
                .andExpect(status().isOk());
    }

    @Test
    void getAlertById_ReturnsOk() throws Exception {
        MonitoredService service = new MonitoredService();
        service.setId(1L);
        service.setName("Order Service");

        AlertRule rule = new AlertRule();
        rule.setId(10L);
        rule.setRuleName("High Latency");

        AlertEvent event = new AlertEvent();
        event.setId(100L);
        event.setService(service);
        event.setRule(rule);
        event.setMetricName("RESPONSE_TIME");
        event.setUnit("MILLISECONDS");
        event.setTriggerValue(150.0);
        event.setThresholdValue(100.0);
        event.setSeverity("HIGH");
        event.setStatus("TRIGGERED");
        event.setMessage("Breached threshold");
        event.setTriggeredAt(Instant.parse("2026-10-21T07:00:00Z"));

        when(alertService.getAlertById(100L)).thenReturn(event);

        mockMvc.perform(get("/api/alerts/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("TRIGGERED"));
    }

    @Test
    void getAlertById_Nonexistent_ReturnsNotFound() throws Exception {
        when(alertService.getAlertById(999L))
                .thenThrow(new ResourceNotFoundException("Alert not found with id: 999"));

        mockMvc.perform(get("/api/alerts/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void acknowledgeAlert_ReturnsOk() throws Exception {
        MonitoredService service = new MonitoredService();
        service.setId(1L);
        service.setName("Order Service");

        AlertRule rule = new AlertRule();
        rule.setId(10L);
        rule.setRuleName("High Latency");

        AlertEvent event = new AlertEvent();
        event.setId(100L);
        event.setService(service);
        event.setRule(rule);
        event.setMetricName("RESPONSE_TIME");
        event.setUnit("MILLISECONDS");
        event.setTriggerValue(150.0);
        event.setThresholdValue(100.0);
        event.setSeverity("HIGH");
        event.setStatus("ACKNOWLEDGED");
        event.setMessage("Breached threshold");
        event.setTriggeredAt(Instant.parse("2026-10-21T07:00:00Z"));

        when(alertService.acknowledgeAlert(100L)).thenReturn(event);

        mockMvc.perform(patch("/api/alerts/100/acknowledge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }
}
