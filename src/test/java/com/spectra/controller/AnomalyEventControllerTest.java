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

import com.spectra.entity.AnomalyEvent;
import com.spectra.entity.MonitoredService;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.GlobalExceptionHandler;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.service.AnomalyDetectionService;

@ExtendWith(MockitoExtension.class)
class AnomalyEventControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AnomalyDetectionService anomalyDetectionService;

    @InjectMocks
    private AnomalyEventController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private AnomalyEvent createSampleAnomaly(Long id) {
        MonitoredService service = new MonitoredService();
        service.setId(10L);
        service.setName("Payment Gateway");

        AnomalyEvent event = new AnomalyEvent();
        event.setId(id);
        event.setService(service);
        event.setMetricName("RESPONSE_TIME");
        event.setUnit("MILLISECONDS");
        event.setObservedValue(500.0);
        event.setBaselineMean(100.0);
        event.setBaselineStdDev(20.0);
        event.setAnomalyScore(20.0);
        event.setSeverity("CRITICAL");
        event.setDirection("POSITIVE_SPIKE");
        event.setStatus("DETECTED");
        event.setMessage("Statistical anomaly detected: high latency spike");
        event.setDetectedAt(Instant.now());
        return event;
    }

    @Test
    void getRecentAnomalies_ReturnsOk() throws Exception {
        when(anomalyDetectionService.getRecentAnomalies(50))
                .thenReturn(List.of(createSampleAnomaly(1L), createSampleAnomaly(2L)));

        mockMvc.perform(get("/api/anomalies/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$[0].direction").value("POSITIVE_SPIKE"))
                .andExpect(jsonPath("$[0].serviceName").value("Payment Gateway"));
    }

    @Test
    void getRecentAnomalies_CustomLimit_PassesParam() throws Exception {
        when(anomalyDetectionService.getRecentAnomalies(10))
                .thenReturn(List.of(createSampleAnomaly(1L)));

        mockMvc.perform(get("/api/anomalies/recent?limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getRecentAnomalies_InvalidLimit_ReturnsBadRequest() throws Exception {
        when(anomalyDetectionService.getRecentAnomalies(0))
                .thenThrow(new BadRequestException("Query limit must be between 1 and 100"));

        mockMvc.perform(get("/api/anomalies/recent?limit=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Query limit must be between 1 and 100"));
    }

    @Test
    void getAnomaliesByServiceId_ReturnsOk() throws Exception {
        when(anomalyDetectionService.getAnomaliesByServiceId(eq(10L), eq(50)))
                .thenReturn(List.of(createSampleAnomaly(1L)));

        mockMvc.perform(get("/api/anomalies/service/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].serviceId").value(10));
    }

    @Test
    void getAnomaliesByServiceId_NotFound_ReturnsNotFound() throws Exception {
        when(anomalyDetectionService.getAnomaliesByServiceId(eq(999L), eq(50)))
                .thenThrow(new ResourceNotFoundException("Service not found with id: 999"));

        mockMvc.perform(get("/api/anomalies/service/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getAnomalyById_ReturnsOk() throws Exception {
        when(anomalyDetectionService.getAnomalyById(1L))
                .thenReturn(createSampleAnomaly(1L));

        mockMvc.perform(get("/api/anomalies/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("DETECTED"))
                .andExpect(jsonPath("$.observedValue").value(500.0));
    }

    @Test
    void getAnomalyById_NotFound_ReturnsNotFound() throws Exception {
        when(anomalyDetectionService.getAnomalyById(999L))
                .thenThrow(new ResourceNotFoundException("Anomaly event not found with id: 999"));

        mockMvc.perform(get("/api/anomalies/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void acknowledgeAnomaly_ReturnsOk() throws Exception {
        AnomalyEvent acknowledged = createSampleAnomaly(1L);
        acknowledged.setStatus("ACKNOWLEDGED");
        acknowledged.setAcknowledgedBy("admin@spectra.local");
        acknowledged.setAcknowledgedAt(Instant.now());

        when(anomalyDetectionService.acknowledgeAnomaly(1L))
                .thenReturn(acknowledged);

        mockMvc.perform(patch("/api/anomalies/1/acknowledge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.acknowledgedBy").value("admin@spectra.local"));
    }
}
