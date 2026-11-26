package com.spectra.integration;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * End-to-end integration test verifying that the Spring Boot application
 * remains completely operational when the Python AI microservice is offline / unreachable.
 */
@SpringBootTest
@ActiveProfiles("test")
class AiEndpointOfflineIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("AI Offline: Core /api/health endpoint remains UP and unaffected")
    void coreHealth_RemainsUpWhenAiOffline() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("AI Offline: /api/ai/health returns UNAVAILABLE fallback without throwing 500")
    void aiHealth_ReturnsUnavailableFallback() throws Exception {
        mockMvc.perform(get("/api/ai/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"));
    }

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("AI Offline: /api/ai/rca returns deterministic rule-based fallback without throwing 500")
    void aiRca_ReturnsDeterministicFallback() throws Exception {
        String payload = """
                {
                    "serviceId": 1,
                    "serviceName": "PaymentService",
                    "incidentTitle": "Memory saturation",
                    "recentMetrics": []
                }
                """;

        mockMvc.perform(post("/api/ai/rca")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primary_root_cause").value("Deterministic Fallback: Anomaly in service metrics"))
                .andExpect(jsonPath("$.confidence_score").value(0.60))
                .andExpect(jsonPath("$.failure_category").value("GENERAL_ANOMALY"));
    }

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("AI Offline: /api/ai/forecast returns FALLBACK status without throwing 500")
    void aiForecast_ReturnsFallback() throws Exception {
        String payload = """
                {
                    "metricName": "CPU_USAGE",
                    "unit": "PERCENT",
                    "values": [50.0, 60.0, 70.0],
                    "criticalThreshold": 90.0
                }
                """;

        mockMvc.perform(post("/api/ai/forecast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FALLBACK"))
                .andExpect(jsonPath("$.trend_direction").value("UNKNOWN"));
    }

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("AI Offline: /api/ai/runbooks returns safe empty result without throwing 500")
    void aiRunbooks_ReturnsSafeEmptyMatches() throws Exception {
        String payload = """
                {
                    "query": "Heap exhaustion",
                    "topK": 2
                }
                """;

        mockMvc.perform(post("/api/ai/runbooks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results_count").value(0))
                .andExpect(jsonPath("$.matches").isEmpty());
    }

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("AI Offline: /api/ai/copilot returns local fallback response without throwing 500")
    void aiCopilot_ReturnsLocalFallback() throws Exception {
        String payload = """
                {
                    "message": "What is the status of payment service?",
                    "operationalContext": {}
                }
                """;

        mockMvc.perform(post("/api/ai/copilot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engine_used").value("SPRING_BOOT_FALLBACK"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("AI Offline: Core service management remains 100% operational")
    void coreServices_RemainOperationalWhenAiOffline() throws Exception {
        mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk());
    }
}
