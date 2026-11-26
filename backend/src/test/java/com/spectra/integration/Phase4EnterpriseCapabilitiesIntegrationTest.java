package com.spectra.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import com.spectra.entity.AuditLog;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.repository.AuditLogRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
public class Phase4EnterpriseCapabilitiesIntegrationTest {

    private static final String ADMIN_EMAIL = "enterprise_admin@spectra.local";
    private static final String OPS_EMAIL = "enterprise_ops@spectra.local";
    private static final String USER_TENANT_A_EMAIL = "tenant_a_user@spectra.local";
    private static final String USER_TENANT_B_EMAIL = "tenant_b_user@spectra.local";
    private static final String RAW_PASSWORD = "Password123!";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private User adminUser;
    private User opsUser;
    private User tenantAUser;
    private User tenantBUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        adminUser = userRepository.findByEmail(ADMIN_EMAIL).orElseGet(() -> {
            User u = new User();
            u.setEmail(ADMIN_EMAIL);
            u.setFullName("Enterprise Administrator");
            u.setPassword(passwordEncoder.encode(RAW_PASSWORD));
            u.setRole(Role.ADMIN);
            return userRepository.save(u);
        });

        opsUser = userRepository.findByEmail(OPS_EMAIL).orElseGet(() -> {
            User u = new User();
            u.setEmail(OPS_EMAIL);
            u.setFullName("Operations Specialist");
            u.setPassword(passwordEncoder.encode(RAW_PASSWORD));
            u.setRole(Role.OPERATIONS);
            return userRepository.save(u);
        });

        tenantAUser = userRepository.findByEmail(USER_TENANT_A_EMAIL).orElseGet(() -> {
            User u = new User();
            u.setEmail(USER_TENANT_A_EMAIL);
            u.setFullName("Tenant A User");
            u.setPassword(passwordEncoder.encode(RAW_PASSWORD));
            u.setRole(Role.USER);
            return userRepository.save(u);
        });

        tenantBUser = userRepository.findByEmail(USER_TENANT_B_EMAIL).orElseGet(() -> {
            User u = new User();
            u.setEmail(USER_TENANT_B_EMAIL);
            u.setFullName("Tenant B User");
            u.setPassword(passwordEncoder.encode(RAW_PASSWORD));
            u.setRole(Role.USER);
            return userRepository.save(u);
        });
    }

    @Test
    @DisplayName("Phase 4.1: Administrator Role Full Operational & Governance Access")
    void testAdminRolePermissions() throws Exception {
        // 1. Admin can view audit logs
        mockMvc.perform(get("/api/audit-logs")
                        .with(httpBasic(ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk());

        // 2. Admin can access Actuator metrics
        mockMvc.perform(get("/actuator/metrics")
                        .with(httpBasic(ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk());

        // 3. Admin can update user role
        mockMvc.perform(patch("/api/users/" + tenantAUser.getId() + "/role?role=OPERATIONS")
                        .with(httpBasic(ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OPERATIONS"));

        // Revert role back to USER
        mockMvc.perform(patch("/api/users/" + tenantAUser.getId() + "/role?role=USER")
                        .with(httpBasic(ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));

        // 4. Admin is read-only on operational resources and cannot delete services (Forbidden 403)
        MonitoredService ephemeral = new MonitoredService();
        ephemeral.setName("Admin-Disposable-Service-" + System.nanoTime());
        ephemeral.setDescription("Temporary for deletion test");
        ephemeral.setEndpointUrl("https://disposable.spectra.internal");
        ephemeral.setEnvironment("DEVELOPMENT");
        ephemeral.setActive(true);
        ephemeral.setUser(tenantAUser);
        ephemeral = serviceRepository.save(ephemeral);

        mockMvc.perform(delete("/api/services/" + ephemeral.getId())
                        .with(httpBasic(ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isForbidden());

        assertTrue(serviceRepository.existsById(ephemeral.getId()));
    }

    @Test
    @DisplayName("Phase 4.2: Operations Role Scoped Access & Restriction Enforcement")
    void testOperationsRoleRestrictions() throws Exception {
        // 1. Operations role CAN create services
        String servicePayload = """
                {
                    "name": "Ops-Created-Service-%d",
                    "description": "Created by Operations",
                    "endpointUrl": "https://ops-service.spectra.internal",
                    "environment": "STAGING",
                    "active": true
                }
                """.formatted(System.nanoTime());

        MvcResult result = mockMvc.perform(post("/api/services")
                        .with(httpBasic(OPS_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(servicePayload))
                .andExpect(status().isCreated())
                .andReturn();

        Number svcIdNum = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        Long serviceId = svcIdNum.longValue();

        // 2. Operations role CAN manage/delete services within its own enterprise
        mockMvc.perform(delete("/api/services/" + serviceId)
                        .with(httpBasic(OPS_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNoContent());

        // 3. Operations role CANNOT escalate user roles (Forbidden 403)
        mockMvc.perform(patch("/api/users/" + tenantBUser.getId() + "/role?role=ADMIN")
                        .with(httpBasic(OPS_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isForbidden());

        // 4. Operations role CANNOT view audit logs (Forbidden 403)
        mockMvc.perform(get("/api/audit-logs")
                        .with(httpBasic(OPS_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isForbidden());

        // 5. Operations role CANNOT access administrative Actuator (Forbidden 403)
        mockMvc.perform(get("/actuator/metrics")
                        .with(httpBasic(OPS_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Phase 4.3: Unauthenticated Access Rejection and Public Endpoint Whitelisting")
    void testAuthenticationEnforcement() throws Exception {
        // Protected endpoints MUST return 401 Unauthorized when unauthenticated
        mockMvc.perform(get("/api/services"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/telemetry/external")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isUnauthorized());

        // Whitelisted public endpoints MUST be accessible without authentication
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Phase 4.4: Multi-Tenant Data Isolation Enforcement")
    void testTenantIsolationEnforcement() throws Exception {
        // Tenant A creates a service
        String tenantAServicePayload = """
                {
                    "name": "Tenant-A-Finance-Core-%d",
                    "description": "Tenant A Core",
                    "endpointUrl": "https://tenant-a.internal",
                    "environment": "PRODUCTION",
                    "active": true
                }
                """.formatted(System.nanoTime());

        MvcResult result = mockMvc.perform(post("/api/services")
                        .with(httpBasic(USER_TENANT_A_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tenantAServicePayload))
                .andExpect(status().isCreated())
                .andReturn();

        Number svcIdNum = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        Long tenantAServiceId = svcIdNum.longValue();

        // 1. Tenant A can see their own service
        mockMvc.perform(get("/api/services/" + tenantAServiceId)
                        .with(httpBasic(USER_TENANT_A_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk());

        // 2. Tenant B CANNOT see Tenant A's service (Returns 404 Not Found to prevent ID enumeration)
        mockMvc.perform(get("/api/services/" + tenantAServiceId)
                        .with(httpBasic(USER_TENANT_B_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNotFound());

        // 3. Tenant B listing does not contain Tenant A's service
        mockMvc.perform(get("/api/services")
                        .with(httpBasic(USER_TENANT_B_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + tenantAServiceId + ")]").doesNotExist());

        // 4. Tenant B cannot query metrics for Tenant A's service
        mockMvc.perform(get("/api/metrics/service/" + tenantAServiceId)
                        .with(httpBasic(USER_TENANT_B_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Phase 4.5: Auditability - Audit Log Generation for Governance Actions")
    void testAuditLogCreation() throws Exception {
        long initialCount = auditLogRepository.count();

        // Perform an administrative action: update user role
        mockMvc.perform(patch("/api/users/" + tenantBUser.getId() + "/role?role=OPERATIONS")
                        .with(httpBasic(ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk());

        // Verify audit log entry was created
        long updatedCount = auditLogRepository.count();
        assertTrue(updatedCount > initialCount, "Audit log count must increase after role update");
        List<AuditLog> logs = auditLogRepository.findAllByOrderByCreatedAtDesc(
                org.springframework.data.domain.PageRequest.of(0, 10)
        );
        AuditLog latest = logs.get(0);
        assertEquals("USER_ROLE_UPDATED", latest.getAction());
        assertEquals(ADMIN_EMAIL, latest.getPerformedBy());
        assertTrue(latest.getDetails().contains("OPERATIONS"));

        // Revert role
        mockMvc.perform(patch("/api/users/" + tenantBUser.getId() + "/role?role=USER")
                        .with(httpBasic(ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk());
    }
}
