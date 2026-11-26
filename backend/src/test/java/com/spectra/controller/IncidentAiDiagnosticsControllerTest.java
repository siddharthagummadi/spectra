package com.spectra.controller;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.spectra.dto.IncidentAiDiagnosticsResponse;
import com.spectra.entity.Incident;
import com.spectra.entity.IncidentAiAnalysis;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.service.IncidentAiService;

@SpringBootTest
@ActiveProfiles("test")
class IncidentAiDiagnosticsControllerTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private IncidentAiService incidentAiService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // --- 1. Authentication & Security Boundaries ---

    @Test
    @DisplayName("Security: Unauthenticated POST to /ai-diagnostics is rejected with HTTP 401")
    void unauthenticated_PostDiagnostics_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/incidents/1/ai-diagnostics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Security: Unauthenticated GET to /ai-diagnostics is rejected with HTTP 401")
    void unauthenticated_GetDiagnostics_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/incidents/1/ai-diagnostics"))
                .andExpect(status().isUnauthorized());
    }

    // --- 2. Authenticated USER Operations ---

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("USER Role: Can trigger incident AI diagnostics successfully")
    void userRole_PostDiagnostics_ReturnsOk() throws Exception {
        Incident incident = new Incident();
        incident.setId(42L);

        IncidentAiAnalysis analysis = new IncidentAiAnalysis();
        analysis.setId(100L);
        analysis.setIncident(incident);
        analysis.setStatus("COMPLETED");
        analysis.setPrimaryRootCause("JVM Heap Exhaustion");
        analysis.setConfidenceScore(0.92);
        analysis.setVersion(1);
        analysis.setExecutionId(UUID.randomUUID().toString());
        analysis.setCompletedAt(Instant.now());

        when(incidentAiService.requestOrRunDiagnostics(eq(42L), anyBoolean())).thenReturn(analysis);

        mockMvc.perform(post("/api/incidents/42/ai-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forceReanalyze\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.incidentId").value(42))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.primaryRootCause").value("JVM Heap Exhaustion"))
                .andExpect(jsonPath("$.confidenceScore").value(0.92))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("USER Role: Can retrieve existing persisted incident AI diagnostics")
    void userRole_GetDiagnostics_ReturnsOk() throws Exception {
        Incident incident = new Incident();
        incident.setId(42L);

        IncidentAiAnalysis analysis = new IncidentAiAnalysis();
        analysis.setId(100L);
        analysis.setIncident(incident);
        analysis.setStatus("COMPLETED");
        analysis.setPrimaryRootCause("Database Connection Pool Starvation");
        analysis.setConfidenceScore(0.88);
        analysis.setVersion(1);
        analysis.setExecutionId(UUID.randomUUID().toString());
        analysis.setCompletedAt(Instant.now());

        when(incidentAiService.getDiagnosticsSummary(42L))
                .thenReturn(IncidentAiDiagnosticsResponse.of(42L, analysis, analysis));

        mockMvc.perform(get("/api/incidents/42/ai-diagnostics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(42))
                .andExpect(jsonPath("$.latestExecution.status").value("COMPLETED"))
                .andExpect(jsonPath("$.latestExecution.primaryRootCause").value("Database Connection Pool Starvation"))
                .andExpect(jsonPath("$.lastSuccessful.status").value("COMPLETED"));
    }

    // --- 3. Authenticated ADMIN Operations ---

    @Test
    @WithMockUser(username = "admin@spectra.local", roles = {"ADMIN"})
    @DisplayName("ADMIN Role: Read-only restriction blocks triggering diagnostics (HTTP 403)")
    void adminRole_PostDiagnostics_ForceReanalyze_ReturnsOk() throws Exception {
        mockMvc.perform(post("/api/incidents/42/ai-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forceReanalyze\": true}"))
                .andExpect(status().isForbidden());
    }

    // --- 4. Not Found Scenarios ---

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("Not Found: GET returns HTTP 404 when no analysis exists for the incident")
    void getDiagnostics_NotFound_Returns404() throws Exception {
        when(incidentAiService.getDiagnosticsSummary(999L))
                .thenThrow(new ResourceNotFoundException("No AI diagnostics analysis found for incident ID: 999"));

        mockMvc.perform(get("/api/incidents/999/ai-diagnostics"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No AI diagnostics analysis found for incident ID: 999"));
    }

    @Test
    @WithMockUser(username = "operator@spectra.local", roles = {"USER"})
    @DisplayName("Not Found: POST returns HTTP 404 when incident does not exist")
    void postDiagnostics_IncidentNotFound_Returns404() throws Exception {
        when(incidentAiService.requestOrRunDiagnostics(eq(999L), anyBoolean()))
                .thenThrow(new ResourceNotFoundException("Incident not found with id: 999"));

        mockMvc.perform(post("/api/incidents/999/ai-diagnostics"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Incident not found with id: 999"));
    }
}
