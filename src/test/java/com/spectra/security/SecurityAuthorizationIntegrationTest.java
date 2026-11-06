package com.spectra.security;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
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
class SecurityAuthorizationIntegrationTest {

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
    void unauthenticatedRequest_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/services"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpoints_PermitAll() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void registerEndpoint_CreatesUserIgnoringRole() throws Exception {
        String uniqueEmail = "test_sec_" + System.currentTimeMillis() + "@spectra.local";
        String payload = """
                {
                    "fullName": "Security Test User",
                    "email": "%s",
                    "password": "Password123!",
                    "role": "ADMIN"
                }
                """.formatted(uniqueEmail);

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @WithMockUser(username = "regular_user@spectra.local", roles = {"USER"})
    void userRole_CanReadServices() throws Exception {
        mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "regular_user@spectra.local", roles = {"USER"})
    void userRole_CanCreateService_ReturnsCreated() throws Exception {
        String payload = """
                {
                    "name": "User Allowed Service %d",
                    "description": "User created service",
                    "endpointUrl": "https://user.example.com",
                    "environment": "PRODUCTION",
                    "active": true
                }
                """.formatted(System.currentTimeMillis());

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "regular_user@spectra.local", roles = {"USER"})
    void userRole_CanSubmitMetric_NotForbidden() throws Exception {
        String payload = """
                {
                    "serviceId": 999999,
                    "metricName": "CPU_USAGE",
                    "metricValue": 50.0,
                    "unit": "PERCENT"
                }
                """;

        mockMvc.perform(post("/api/metrics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "regular_user@spectra.local", roles = {"USER"})
    void userRole_CanAttemptAlertRuleCreation_NotForbidden() throws Exception {
        String payload = """
                {
                    "serviceId": 999999,
                    "ruleName": "User Allowed Rule Attempt",
                    "metricName": "CPU_USAGE",
                    "unit": "PERCENT",
                    "comparator": "GREATER_THAN",
                    "thresholdValue": 80.0,
                    "severity": "HIGH"
                }
                """;

        mockMvc.perform(post("/api/alert-rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin_user@spectra.local", roles = {"ADMIN"})
    void adminRole_CanAttemptServiceCreation_NotForbidden() throws Exception {
        String payload = """
                {
                    "name": "Admin Test Service %d",
                    "description": "Admin created service",
                    "endpointUrl": "https://admin.example.com",
                    "environment": "PRODUCTION",
                    "active": true
                }
                """.formatted(System.currentTimeMillis());

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "regular_user@spectra.local", roles = {"USER"})
    void userRole_CanReadAlertRules() throws Exception {
        mockMvc.perform(get("/api/alert-rules"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "regular_user@spectra.local", roles = {"USER"})
    void userRole_CanReadAlerts() throws Exception {
        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "regular_user@spectra.local", roles = {"USER"})
    void userRole_CanReadIncidents() throws Exception {
        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isOk());
    }
}
