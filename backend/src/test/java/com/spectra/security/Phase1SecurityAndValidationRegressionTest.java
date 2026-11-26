package com.spectra.security;

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

@SpringBootTest
@ActiveProfiles("test")
class Phase1SecurityAndValidationRegressionTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // --- 1. Actuator Endpoint Hardening Tests ---

    @Test
    @DisplayName("Public Health Endpoint: /api/health is accessible without authentication")
    void publicHealthEndpoint_PermittedWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Actuator Health Endpoint: /actuator/health is accessible without authentication")
    void actuatorHealth_PermittedWithoutAuth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Actuator Prometheus: Unauthenticated access is rejected with HTTP 401")
    void actuatorPrometheus_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Actuator Metrics: Unauthenticated access is rejected with HTTP 401")
    void actuatorMetrics_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("Actuator Prometheus: USER role is forbidden with HTTP 403")
    void actuatorPrometheus_UserRole_ReturnsForbidden() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@spectra.local", roles = {"ADMIN"})
    @DisplayName("Actuator Prometheus: ADMIN role is authorized with HTTP 200")
    void actuatorPrometheus_AdminRole_ReturnsOk() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@spectra.local", roles = {"ADMIN"})
    @DisplayName("Actuator Metrics: ADMIN role is authorized with HTTP 200")
    void actuatorMetrics_AdminRole_ReturnsOk() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk());
    }

    // --- 2. Demo Endpoints Isolation & Security Tests ---

    @Test
    @DisplayName("Demo Quick-Launch: Unauthenticated access is rejected with HTTP 401")
    void demoQuickLaunch_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/demo/quick-launch"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin@spectra.local", roles = {"ADMIN"})
    @DisplayName("Demo Endpoints: Inactive outside 'demo' profile returns HTTP 404")
    void demoEndpoints_InactiveOutsideDemoProfile_ReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/demo/quick-launch"))
                .andExpect(status().isNotFound());
    }

    // --- 3. Telemetry Ingestion Validation Hardening Tests ---

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("Telemetry Validation: Negative value is rejected with HTTP 400")
    void telemetryValidation_NegativeValue_ReturnsBadRequest() throws Exception {
        String payload = """
                {
                    "serviceId": 1,
                    "metricName": "CPU_USAGE",
                    "metricValue": -5.0,
                    "unit": "PERCENT"
                }
                """;

        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Metric value cannot be negative"));
    }

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("Telemetry Validation: Percentage exceeding 100.0 is rejected with HTTP 400")
    void telemetryValidation_PercentageOver100_ReturnsBadRequest() throws Exception {
        String payload = """
                {
                    "serviceId": 1,
                    "metricName": "CPU_USAGE",
                    "metricValue": 105.0,
                    "unit": "PERCENT"
                }
                """;

        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Percentage metric value for CPU_USAGE must be between 0.0 and 100.0"));
    }

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("Telemetry Validation: Excessive response time is rejected with HTTP 400")
    void telemetryValidation_ExcessiveResponseTime_ReturnsBadRequest() throws Exception {
        String payload = """
                {
                    "serviceId": 1,
                    "metricName": "RESPONSE_TIME",
                    "metricValue": 5000000.0,
                    "unit": "MILLISECONDS"
                }
                """;

        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Response time value for RESPONSE_TIME exceeds maximum threshold (3,600,000 ms)"));
    }

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("Telemetry Validation: Future timestamp is rejected with HTTP 400")
    void telemetryValidation_FutureTimestamp_ReturnsBadRequest() throws Exception {
        String payload = """
                {
                    "serviceId": 1,
                    "metricName": "CPU_USAGE",
                    "metricValue": 45.0,
                    "unit": "PERCENT",
                    "recordedAt": "2099-01-01T00:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Measurement timestamp cannot be in the future"));
    }

    // --- 4. Service Registration Validation Hardening Tests ---

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("Service Validation: Blank service name is rejected with HTTP 400")
    void serviceValidation_BlankName_ReturnsBadRequest() throws Exception {
        String payload = """
                {
                    "name": "   ",
                    "endpointUrl": "https://service.example.com",
                    "environment": "PRODUCTION"
                }
                """;

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("Service Validation: Invalid endpoint URL is rejected with HTTP 400")
    void serviceValidation_InvalidUrl_ReturnsBadRequest() throws Exception {
        String payload = """
                {
                    "name": "ValidService",
                    "endpointUrl": "not_a_valid_url",
                    "environment": "PRODUCTION"
                }
                """;

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("Service Validation: Invalid environment is rejected with HTTP 400")
    void serviceValidation_InvalidEnvironment_ReturnsBadRequest() throws Exception {
        String payload = """
                {
                    "name": "ValidService",
                    "endpointUrl": "https://service.example.com",
                    "environment": "UNKNOWN_ENV"
                }
                """;

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    // --- 5. Unauthenticated Access Denial Tests ---

    @Test
    @DisplayName("Protected Endpoints: Unauthenticated access to /api/metrics is rejected with HTTP 401")
    void unauthenticated_MetricsEndpoint_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/metrics/recent"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected Endpoints: Unauthenticated access to /api/alerts is rejected with HTTP 401")
    void unauthenticated_AlertsEndpoint_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected Endpoints: Unauthenticated access to /api/incidents is rejected with HTTP 401")
    void unauthenticated_IncidentsEndpoint_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected Endpoints: Unauthenticated access to /api/alert-rules is rejected with HTTP 401")
    void unauthenticated_AlertRulesEndpoint_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/alert-rules"))
                .andExpect(status().isUnauthorized());
    }

    // --- 6. External Database Credential Resolution Verification ---

    @Test
    @DisplayName("Database Security: Password configuration does not contain hardcoded default '1605'")
    void databaseConfiguration_NoHardcodedDefaultPassword() {
        org.springframework.core.env.Environment env = context.getEnvironment();
        String password = env.getProperty("spring.datasource.password");
        // Verify default fallback is empty, not a hardcoded secret like "1605"
        org.junit.jupiter.api.Assertions.assertNotEquals("1605", password,
                "Database password must never fall back to hardcoded default '1605'");
    }
}
