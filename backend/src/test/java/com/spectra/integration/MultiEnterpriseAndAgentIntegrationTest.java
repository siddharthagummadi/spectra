package com.spectra.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.spectra.entity.Enterprise;
import com.spectra.entity.EnterpriseAgent;
import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.repository.EnterpriseAgentRepository;
import com.spectra.repository.EnterpriseRepository;
import com.spectra.repository.ServiceMetricRepository;
import com.spectra.repository.UserRepository;
import com.spectra.service.AgentStalenessMonitor;

@SpringBootTest
@ActiveProfiles("test")
class MultiEnterpriseAndAgentIntegrationTest {

    private static final String ADMIN_EMAIL = "platform_admin@spectra.local";
    private static final String PASSWORD = "Password123!";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EnterpriseRepository enterpriseRepository;
    @Autowired
    private EnterpriseAgentRepository agentRepository;
    @Autowired
    private ServiceMetricRepository metricRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private AgentStalenessMonitor stalenessMonitor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        userRepository.findByEmail(ADMIN_EMAIL).orElseGet(() -> {
            User admin = new User();
            admin.setFullName("Platform Admin");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPassword(passwordEncoder.encode(PASSWORD));
            admin.setRole(Role.ADMIN);
            return userRepository.save(admin);
        });
    }

    @Test
    void fullEnterpriseOnboarding_AgentTelemetry_Deduplication_CredentialRotation_AndIsolation() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis());
        String orgCodeA = "ACME_" + suffix;
        String orgCodeB = "BETA_" + suffix;

        // 1. Platform Admin creates Enterprise A -> PENDING + single-use enrollment token
        MvcResult createEntResult = mockMvc.perform(post("/api/admin/enterprises")
                        .with(httpBasic(ADMIN_EMAIL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Acme Corp %s",
                                    "organizationCode": "%s",
                                    "environment": "PRODUCTION",
                                    "contactName": "Alice",
                                    "contactEmail": "alice@acme.test"
                                }
                                """.formatted(suffix, orgCodeA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enterprise.connectionStatus").value("PENDING"))
                .andExpect(jsonPath("$.enrollment.enrollmentToken").isString())
                .andReturn();

        String createBody = createEntResult.getResponse().getContentAsString();
        Number entIdNum = JsonPath.read(createBody, "$.enterprise.id");
        Long enterpriseAId = entIdNum.longValue();
        String enrollmentToken = JsonPath.read(createBody, "$.enrollment.enrollmentToken");

        // Create Enterprise B for isolation assertions
        MvcResult createEntBResult = mockMvc.perform(post("/api/admin/enterprises")
                        .with(httpBasic(ADMIN_EMAIL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Beta Corp %s",
                                    "organizationCode": "%s",
                                    "environment": "STAGING"
                                }
                                """.formatted(suffix, orgCodeB)))
                .andExpect(status().isCreated())
                .andReturn();
        Long enterpriseBId = ((Number) JsonPath.read(createEntBResult.getResponse().getContentAsString(), "$.enterprise.id")).longValue();

        // 2. Register two Enterprise Users in Enterprise A and one in Enterprise B
        String userA1 = "a1_" + suffix + "@acme.test";
        String userA2 = "a2_" + suffix + "@acme.test";
        String userB1 = "b1_" + suffix + "@beta.test";

        for (String email : new String[]{userA1, userA2}) {
            mockMvc.perform(post("/api/users/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "fullName": "Acme User",
                                        "email": "%s",
                                        "password": "%s",
                                        "organizationCode": "%s"
                                    }
                                    """.formatted(email, PASSWORD, orgCodeA)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.enterpriseId").value(enterpriseAId));
        }

        mockMvc.perform(post("/api/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "fullName": "Beta User",
                                    "email": "%s",
                                    "password": "%s",
                                    "organizationCode": "%s"
                                }
                                """.formatted(userB1, PASSWORD, orgCodeB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enterpriseId").value(enterpriseBId));

        // Enterprise User cannot call Platform Admin APIs
        mockMvc.perform(get("/api/admin/overview")
                        .with(httpBasic(userA1, PASSWORD)))
                .andExpect(status().isForbidden());

        // 3. Agent enrolls with temporary single-use token -> receives permanent credential
        MvcResult enrollRes = mockMvc.perform(post("/api/agent/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "enrollmentToken": "%s",
                                    "agentCode": "acme-agent-01",
                                    "hostname": "prod-node-1.acme.internal",
                                    "osInfo": "Linux 6.8",
                                    "agentVersion": "1.0.0"
                                }
                                """.formatted(enrollmentToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enterpriseId").value(enterpriseAId))
                .andExpect(jsonPath("$.agentCredential").isString())
                .andReturn();

        String enrollBody = enrollRes.getResponse().getContentAsString();
        Long agentId = ((Number) JsonPath.read(enrollBody, "$.agentId")).longValue();
        String agentCredential = JsonPath.read(enrollBody, "$.agentCredential");

        // Reusing the same single-use enrollment token must fail
        mockMvc.perform(post("/api/agent/enroll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "enrollmentToken": "%s",
                                    "agentCode": "rogue-agent",
                                    "hostname": "rogue.internal"
                                }
                                """.formatted(enrollmentToken)))
                .andExpect(status().isUnauthorized());

        // Credential non-interchangeability:
        // User Basic Auth rejected on /api/agent/**
        mockMvc.perform(get("/api/agent/config")
                        .with(httpBasic(userA1, PASSWORD)))
                .andExpect(status().isUnauthorized());

        // Agent credential rejected on /api/**
        mockMvc.perform(get("/api/services")
                        .header("Authorization", "Bearer " + agentCredential))
                .andExpect(status().isUnauthorized());

        // 4. Agent sends telemetry batch with infra + app metrics -> Enterprise becomes CONNECTED
        String batchId = "batch-" + suffix;
        String batchPayload = """
                {
                    "clientBatchId": "%s",
                    "samples": [
                        {"serviceName": "Checkout-API", "environment": "PRODUCTION", "metricName": "CPU_USAGE", "metricValue": 88.5, "unit": "PERCENT"},
                        {"serviceName": "Checkout-API", "environment": "PRODUCTION", "metricName": "DISK_USAGE", "metricValue": 64.0, "unit": "PERCENT"},
                        {"serviceName": "Checkout-API", "environment": "PRODUCTION", "metricName": "NETWORK_USAGE", "metricValue": 512.0, "unit": "KBPS"},
                        {"serviceName": "Checkout-API", "environment": "PRODUCTION", "metricName": "SYSTEM_UPTIME", "metricValue": 86400.0, "unit": "SECONDS"},
                        {"serviceName": "Checkout-API", "environment": "PRODUCTION", "metricName": "REQUEST_COUNT", "metricValue": 1240.0, "unit": "COUNT"},
                        {"serviceName": "Checkout-API", "environment": "PRODUCTION", "metricName": "SERVICE_AVAILABILITY", "metricValue": 100.0, "unit": "PERCENT"}
                    ]
                }
                """.formatted(batchId);

        mockMvc.perform(post("/api/agent/telemetry/batch")
                        .header("Authorization", "Bearer " + agentCredential)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.acceptedCount").value(6))
                .andExpect(jsonPath("$.enterpriseStatus").value("CONNECTED"));

        // Duplicate batch replay must be idempotent
        mockMvc.perform(post("/api/agent/telemetry/batch")
                        .header("Authorization", "Bearer " + agentCredential)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicateBatch").value(true))
                .andExpect(jsonPath("$.acceptedCount").value(0));

        // Both User A1 and User A2 in Enterprise A see the auto-discovered service & metrics; User B1 does NOT
        MvcResult a1Services = mockMvc.perform(get("/api/services")
                        .with(httpBasic(userA1, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andReturn();
        Long checkoutServiceId = ((Number) JsonPath.read(a1Services.getResponse().getContentAsString(), "$[0].id")).longValue();

        mockMvc.perform(get("/api/services")
                        .with(httpBasic(userA2, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/services")
                        .with(httpBasic(userB1, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/services/" + checkoutServiceId)
                        .with(httpBasic(userB1, PASSWORD)))
                .andExpect(status().isNotFound());

        // Enterprise user A2 can create and resolve an incident on Checkout-API (shared enterprise collaboration)
        MvcResult incRes = mockMvc.perform(post("/api/incidents")
                        .with(httpBasic(userA2, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "serviceId": %d,
                                    "title": "Checkout CPU Spike",
                                    "severity": "CRITICAL",
                                    "author": "%s"
                                }
                                """.formatted(checkoutServiceId, userA2)))
                .andExpect(status().isCreated())
                .andReturn();
        Long incidentId = ((Number) JsonPath.read(incRes.getResponse().getContentAsString(), "$.id")).longValue();

        // Admin can inspect Enterprise A read-only dashboard, but CANNOT resolve or modify the incident
        mockMvc.perform(get("/api/admin/enterprises/" + enterpriseAId)
                        .with(httpBasic(ADMIN_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readOnly").value(true))
                .andExpect(jsonPath("$.services", hasSize(1)))
                .andExpect(jsonPath("$.incidents", hasSize(1)));

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                        .with(httpBasic(ADMIN_EMAIL, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "status": "RESOLVED",
                                    "author": "%s"
                                }
                                """.formatted(ADMIN_EMAIL)))
                .andExpect(status().isForbidden());

        // 5. Credential rotation & deactivation
        MvcResult rotateRes = mockMvc.perform(post("/api/admin/agents/" + agentId + "/credentials/rotate")
                        .with(httpBasic(ADMIN_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String newCredential = JsonPath.read(rotateRes.getResponse().getContentAsString(), "$.agentCredential");

        mockMvc.perform(post("/api/agent/heartbeat")
                        .header("Authorization", "Bearer " + newCredential)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentVersion\":\"1.0.1\",\"localBufferCount\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ONLINE"));

        // 6. Staleness monitor marks agent & enterprise DISCONNECTED when heartbeat times out
        EnterpriseAgent agentEntity = agentRepository.findById(agentId).orElseThrow();
        agentEntity.setLastHeartbeatAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        agentRepository.save(agentEntity);

        stalenessMonitor.evaluateAt(Instant.now());

        Enterprise updatedEnt = enterpriseRepository.findById(enterpriseAId).orElseThrow();
        assertEquals(Enterprise.STATUS_DISCONNECTED, updatedEnt.getConnectionStatus());
        EnterpriseAgent updatedAgent = agentRepository.findById(agentId).orElseThrow();
        assertEquals(EnterpriseAgent.STATUS_DISCONNECTED, updatedAgent.getStatus());

        // Deactivate agent revokes credentials
        mockMvc.perform(post("/api/admin/agents/" + agentId + "/deactivate")
                        .with(httpBasic(ADMIN_EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/agent/config")
                        .header("Authorization", "Bearer " + newCredential))
                .andExpect(status().isUnauthorized());

        assertNotNull(metricRepository);
    }
}
