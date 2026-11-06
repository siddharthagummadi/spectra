package com.spectra.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.spectra.exception.GlobalExceptionHandler;
import com.spectra.service.AiServiceClient;

@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AiServiceClient aiServiceClient;

    @InjectMocks
    private AiController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAiHealth_ReturnsHealthStatus() throws Exception {
        Map<String, Object> health = Map.of("status", "UP", "service", "spectra-ai-service");
        when(aiServiceClient.checkHealth()).thenReturn(health);

        mockMvc.perform(get("/api/ai/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void analyzeRootCause_ReturnsRcaResult() throws Exception {
        Map<String, Object> rcaResult = Map.of(
                "primary_root_cause", "JVM Heap Exhaustion",
                "confidence_score", 0.92,
                "failure_category", "MEMORY_LEAK"
        );
        when(aiServiceClient.analyzeRootCause(anyLong(), anyString(), anyString(), any(), anyList(), any(), any()))
                .thenReturn(rcaResult);

        mockMvc.perform(post("/api/ai/rca")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "serviceId": 1,
                            "serviceName": "Billing",
                            "incidentTitle": "Out of memory error",
                            "recentMetrics": []
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primary_root_cause").value("JVM Heap Exhaustion"))
                .andExpect(jsonPath("$.failure_category").value("MEMORY_LEAK"));
    }

    @Test
    void forecastTelemetry_ReturnsProjections() throws Exception {
        Map<String, Object> forecast = Map.of(
                "status", "SUCCESS",
                "trend_direction", "INCREASING",
                "time_to_breach_minutes", 12.5
        );
        when(aiServiceClient.forecastTrajectory(anyString(), anyString(), anyList(), any()))
                .thenReturn(forecast);

        mockMvc.perform(post("/api/ai/forecast")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "metricName": "MEMORY_USAGE",
                            "unit": "PERCENT",
                            "values": [60.0, 70.0, 80.0]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.time_to_breach_minutes").value(12.5));
    }

    @Test
    void queryRunbooks_ReturnsMatches() throws Exception {
        Map<String, Object> runbooks = Map.of(
                "query", "high CPU",
                "results_count", 1,
                "matches", List.of(Map.of("runbook_id", "RB-001", "title", "High CPU Saturation"))
        );
        when(aiServiceClient.queryRunbooks(anyString(), anyInt(), any()))
                .thenReturn(runbooks);

        mockMvc.perform(post("/api/ai/runbooks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "query": "high CPU",
                            "topK": 2
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results_count").value(1))
                .andExpect(jsonPath("$.matches[0].runbook_id").value("RB-001"));
    }

    @Test
    void chatCopilot_ReturnsReply() throws Exception {
        Map<String, Object> chat = Map.of(
                "reply", "Platform health is optimal.",
                "engine_used", "SPECTRA_DETERMINISTIC_COPILOT",
                "status", "SUCCESS"
        );
        when(aiServiceClient.chatCopilot(anyString(), any()))
                .thenReturn(chat);

        mockMvc.perform(post("/api/ai/copilot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "message": "What is the health?"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Platform health is optimal."));
    }
}
