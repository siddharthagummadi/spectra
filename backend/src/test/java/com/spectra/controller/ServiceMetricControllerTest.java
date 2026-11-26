package com.spectra.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.spectra.dto.CreateMetricRequest;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.ServiceMetric;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.GlobalExceptionHandler;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.service.ServiceMetricService;

@ExtendWith(MockitoExtension.class)
class ServiceMetricControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ServiceMetricService service;

    @InjectMocks
    private ServiceMetricController controller;

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
    void submitMetric_ValidPayload_ReturnsCreated() throws Exception {
        MonitoredService monitoredService = new MonitoredService();
        monitoredService.setId(1L);
        monitoredService.setName("Order Service");

        ServiceMetric metric = new ServiceMetric();
        metric.setId(100L);
        metric.setService(monitoredService);
        metric.setMetricName("CPU_USAGE");
        metric.setMetricValue(78.5);
        metric.setUnit("PERCENT");
        metric.setRecordedAt(Instant.parse("2026-10-21T06:00:00Z"));
        metric.setCreatedAt(Instant.parse("2026-10-21T06:00:01Z"));

        when(service.submitMetric(any(CreateMetricRequest.class))).thenReturn(metric);

        String jsonPayload = """
                {
                    "serviceId": 1,
                    "metricName": "CPU_USAGE",
                    "metricValue": 78.5,
                    "unit": "PERCENT"
                }
                """;

        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.serviceId").value(1))
                .andExpect(jsonPath("$.serviceName").value("Order Service"))
                .andExpect(jsonPath("$.metricName").value("CPU_USAGE"))
                .andExpect(jsonPath("$.metricValue").value(78.5))
                .andExpect(jsonPath("$.unit").value("PERCENT"));
    }

    @Test
    void submitMetric_MissingValue_ReturnsBadRequest() throws Exception {
        String jsonPayload = """
                {
                    "serviceId": 1,
                    "metricName": "CPU_USAGE"
                }
                """;

        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.metricValue").exists());
    }

    @Test
    void submitMetric_BlankMetricName_ReturnsBadRequest() throws Exception {
        String jsonPayload = """
                {
                    "serviceId": 1,
                    "metricName": "   ",
                    "metricValue": 50.0
                }
                """;

        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.metricName").exists());
    }

    @Test
    void submitMetric_MissingServiceId_ReturnsBadRequest() throws Exception {
        String jsonPayload = """
                {
                    "metricName": "CPU_USAGE",
                    "metricValue": 50.0
                }
                """;

        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.serviceId").exists());
    }

    @Test
    void submitMetric_NonexistentService_ReturnsNotFound() throws Exception {
        when(service.submitMetric(any(CreateMetricRequest.class)))
                .thenThrow(new ResourceNotFoundException("Service not found with id: 999"));

        String jsonPayload = """
                {
                    "serviceId": 999,
                    "metricName": "CPU_USAGE",
                    "metricValue": 50.0
                }
                """;

        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Service not found with id: 999"));
    }

    @Test
    void submitMetric_FutureTimestamp_ReturnsBadRequest() throws Exception {
        when(service.submitMetric(any(CreateMetricRequest.class)))
                .thenThrow(new BadRequestException("Measurement timestamp cannot be in the future"));

        String jsonPayload = """
                {
                    "serviceId": 1,
                    "metricName": "CPU_USAGE",
                    "metricValue": 50.0,
                    "recordedAt": "2030-01-01T00:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Measurement timestamp cannot be in the future"));
    }

    @Test
    void getMetricsByServiceId_ExistingService_ReturnsOk() throws Exception {
        MonitoredService monitoredService = new MonitoredService();
        monitoredService.setId(1L);
        monitoredService.setName("Order Service");

        ServiceMetric metric = new ServiceMetric();
        metric.setId(10L);
        metric.setService(monitoredService);
        metric.setMetricName("RESPONSE_TIME");
        metric.setMetricValue(85.0);
        metric.setUnit("MILLISECONDS");
        metric.setRecordedAt(Instant.parse("2026-10-21T06:00:00Z"));
        metric.setCreatedAt(Instant.parse("2026-10-21T06:00:01Z"));

        when(service.getMetricsByServiceId(eq(1L), eq(50))).thenReturn(List.of(metric));

        mockMvc.perform(get("/api/metrics/service/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].serviceId").value(1))
                .andExpect(jsonPath("$[0].metricName").value("RESPONSE_TIME"))
                .andExpect(jsonPath("$[0].metricValue").value(85.0));
    }

    @Test
    void getMetricsByServiceId_NonexistentService_ReturnsNotFound() throws Exception {
        when(service.getMetricsByServiceId(eq(99L), eq(50)))
                .thenThrow(new ResourceNotFoundException("Service not found with id: 99"));

        mockMvc.perform(get("/api/metrics/service/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getMetricsByServiceId_InvalidLimit_ReturnsBadRequest() throws Exception {
        when(service.getMetricsByServiceId(eq(1L), eq(150)))
                .thenThrow(new BadRequestException("Query limit must be between 1 and 100"));

        mockMvc.perform(get("/api/metrics/service/1").param("limit", "150"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Query limit must be between 1 and 100"));
    }

    @Test
    void getRecentMetrics_ReturnsOk() throws Exception {
        MonitoredService monitoredService = new MonitoredService();
        monitoredService.setId(2L);
        monitoredService.setName("Inventory Service");

        ServiceMetric metric = new ServiceMetric();
        metric.setId(11L);
        metric.setService(monitoredService);
        metric.setMetricName("ERROR_RATE");
        metric.setMetricValue(1.2);
        metric.setUnit("PERCENT");
        metric.setRecordedAt(Instant.parse("2026-10-21T06:00:00Z"));
        metric.setCreatedAt(Instant.parse("2026-10-21T06:00:01Z"));

        when(service.getRecentMetrics(50)).thenReturn(List.of(metric));

        mockMvc.perform(get("/api/metrics/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(11))
                .andExpect(jsonPath("$[0].serviceName").value("Inventory Service"))
                .andExpect(jsonPath("$[0].metricName").value("ERROR_RATE"));
    }
}
