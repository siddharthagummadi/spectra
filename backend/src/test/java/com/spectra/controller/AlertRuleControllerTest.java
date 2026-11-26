package com.spectra.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.spectra.dto.CreateAlertRuleRequest;
import com.spectra.dto.UpdateAlertRuleRequest;
import com.spectra.entity.AlertRule;
import com.spectra.entity.MonitoredService;
import com.spectra.exception.GlobalExceptionHandler;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.service.AlertService;

@ExtendWith(MockitoExtension.class)
class AlertRuleControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AlertService alertService;

    @InjectMocks
    private AlertRuleController controller;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createAlertRule_ValidPayload_ReturnsCreated() throws Exception {
        MonitoredService service = new MonitoredService();
        service.setId(1L);
        service.setName("Auth Service");

        AlertRule rule = new AlertRule();
        rule.setId(10L);
        rule.setService(service);
        rule.setRuleName("High CPU");
        rule.setMetricName("CPU_USAGE");
        rule.setUnit("PERCENT");
        rule.setComparator("GREATER_THAN");
        rule.setThresholdValue(85.0);
        rule.setSeverity("HIGH");
        rule.setActive(true);
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(Instant.now());

        when(alertService.createAlertRule(any(CreateAlertRuleRequest.class))).thenReturn(rule);

        String jsonPayload = """
                {
                    "serviceId": 1,
                    "ruleName": "High CPU",
                    "metricName": "CPU_USAGE",
                    "unit": "PERCENT",
                    "comparator": "GREATER_THAN",
                    "thresholdValue": 85.0,
                    "severity": "HIGH"
                }
                """;

        mockMvc.perform(post("/api/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.ruleName").value("High CPU"))
                .andExpect(jsonPath("$.thresholdValue").value(85.0));
    }

    @Test
    void createAlertRule_BlankRuleName_ReturnsBadRequest() throws Exception {
        String jsonPayload = """
                {
                    "serviceId": 1,
                    "ruleName": "   ",
                    "metricName": "CPU_USAGE",
                    "comparator": "GREATER_THAN",
                    "thresholdValue": 85.0,
                    "severity": "HIGH"
                }
                """;

        mockMvc.perform(post("/api/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.ruleName").exists());
    }

    @Test
    void createAlertRule_MissingThreshold_ReturnsBadRequest() throws Exception {
        String jsonPayload = """
                {
                    "serviceId": 1,
                    "ruleName": "Rule",
                    "metricName": "CPU_USAGE",
                    "comparator": "GREATER_THAN",
                    "severity": "HIGH"
                }
                """;

        mockMvc.perform(post("/api/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.thresholdValue").exists());
    }

    @Test
    void createAlertRule_NonexistentService_ReturnsNotFound() throws Exception {
        when(alertService.createAlertRule(any(CreateAlertRuleRequest.class)))
                .thenThrow(new ResourceNotFoundException("Service not found with id: 999"));

        String jsonPayload = """
                {
                    "serviceId": 999,
                    "ruleName": "Rule",
                    "metricName": "CPU_USAGE",
                    "comparator": "GREATER_THAN",
                    "thresholdValue": 85.0,
                    "severity": "HIGH"
                }
                """;

        mockMvc.perform(post("/api/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getAllRules_ReturnsOk() throws Exception {
        MonitoredService service = new MonitoredService();
        service.setId(1L);

        AlertRule rule = new AlertRule();
        rule.setId(10L);
        rule.setService(service);
        rule.setRuleName("High CPU");

        when(alertService.getAllRules()).thenReturn(List.of(rule));

        mockMvc.perform(get("/api/alert-rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].ruleName").value("High CPU"));
    }

    @Test
    void getRulesByServiceId_ReturnsOk() throws Exception {
        MonitoredService service = new MonitoredService();
        service.setId(1L);

        AlertRule rule = new AlertRule();
        rule.setId(10L);
        rule.setService(service);
        rule.setRuleName("High CPU");

        when(alertService.getRulesByServiceId(1L)).thenReturn(List.of(rule));

        mockMvc.perform(get("/api/alert-rules/service/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    void updateAlertRule_ReturnsOk() throws Exception {
        MonitoredService service = new MonitoredService();
        service.setId(1L);

        AlertRule updated = new AlertRule();
        updated.setId(10L);
        updated.setService(service);
        updated.setRuleName("Updated Rule");
        updated.setThresholdValue(90.0);

        when(alertService.updateAlertRule(eq(10L), any(UpdateAlertRuleRequest.class))).thenReturn(updated);

        String jsonPayload = """
                {
                    "ruleName": "Updated Rule",
                    "metricName": "CPU_USAGE",
                    "comparator": "GREATER_THAN",
                    "thresholdValue": 90.0,
                    "severity": "HIGH"
                }
                """;

        mockMvc.perform(put("/api/alert-rules/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.ruleName").value("Updated Rule"));
    }

    @Test
    void updateAlertRuleStatus_ReturnsOk() throws Exception {
        MonitoredService service = new MonitoredService();
        service.setId(1L);

        AlertRule updated = new AlertRule();
        updated.setId(10L);
        updated.setService(service);
        updated.setActive(false);

        when(alertService.updateAlertRuleStatus(10L, false)).thenReturn(updated);

        mockMvc.perform(patch("/api/alert-rules/10/status").param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deleteAlertRule_ReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/alert-rules/10"))
                .andExpect(status().isNoContent());
    }
}
