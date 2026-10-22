package com.spectra.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.repository.AlertEventRepository;
import com.spectra.repository.AlertRuleRepository;
import com.spectra.repository.IncidentNoteRepository;
import com.spectra.repository.IncidentRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.ServiceMetricRepository;
import com.spectra.repository.UserRepository;

@SpringBootTest
class WorkspaceDataIsolationIntegrationTest {

    private static final String USER_ADMIN_EMAIL = "iso_admin_a@spectra.local";
    private static final String USER_REGULAR_B_EMAIL = "iso_user_b@spectra.local";
    private static final String USER_REGULAR_C_EMAIL = "iso_user_c@spectra.local";
    private static final String RAW_PASSWORD = "Password123!";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private AlertEventRepository alertEventRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentNoteRepository incidentNoteRepository;

    @Autowired
    private ServiceMetricRepository metricRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private User adminUser;
    private User userB;
    private User userC;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        adminUser = userRepository.findByEmail(USER_ADMIN_EMAIL).orElseGet(() -> {
            User u = new User();
            u.setEmail(USER_ADMIN_EMAIL);
            u.setFullName("Isolation Admin A");
            u.setPassword(passwordEncoder.encode(RAW_PASSWORD));
            u.setRole(Role.ADMIN);
            return userRepository.save(u);
        });

        userB = userRepository.findByEmail(USER_REGULAR_B_EMAIL).orElseGet(() -> {
            User u = new User();
            u.setEmail(USER_REGULAR_B_EMAIL);
            u.setFullName("Isolation User B");
            u.setPassword(passwordEncoder.encode(RAW_PASSWORD));
            u.setRole(Role.USER);
            return userRepository.save(u);
        });

        userC = userRepository.findByEmail(USER_REGULAR_C_EMAIL).orElseGet(() -> {
            User u = new User();
            u.setEmail(USER_REGULAR_C_EMAIL);
            u.setFullName("Isolation User C");
            u.setPassword(passwordEncoder.encode(RAW_PASSWORD));
            u.setRole(Role.USER);
            return userRepository.save(u);
        });

        cleanUserData();
    }

    @AfterEach
    void tearDown() {
        cleanUserData();
    }

    private void cleanUserData() {
        List<Long> testUserIds = List.of(adminUser.getId(), userB.getId(), userC.getId());
        for (Long uid : testUserIds) {
            incidentRepository.findByServiceUserIdOrderByTriggeredAtDescIdDesc(uid).forEach(inc -> {
                incidentNoteRepository.deleteAll(incidentNoteRepository.findByIncidentIdOrderByCreatedAtAsc(inc.getId()));
                incidentRepository.delete(inc);
            });
            alertEventRepository.deleteAll(alertEventRepository.findByServiceUserIdOrderByTriggeredAtDescIdDesc(uid));
            alertRuleRepository.deleteAll(alertRuleRepository.findByServiceUserId(uid));
            metricRepository.deleteAll(metricRepository.findByServiceUserIdOrderByRecordedAtDescIdDesc(uid, org.springframework.data.domain.Pageable.unpaged()));
            serviceRepository.deleteAll(serviceRepository.findByUserIdOrderByCreatedAtDesc(uid));
        }
    }

    @Test
    void userIsolation_UserBCreates_UserCCannotSeeOrMutate() throws Exception {
        String createPayload = """
                {
                    "name": "Auth Service B",
                    "description": "User B Auth",
                    "endpointUrl": "https://auth-b.spectra.local",
                    "environment": "PRODUCTION",
                    "active": true
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/services")
                        .with(httpBasic(USER_REGULAR_B_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Auth Service B"))
                .andReturn();

        Number serviceIdNum = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        Long serviceId = serviceIdNum.longValue();

        mockMvc.perform(get("/api/services")
                        .with(httpBasic(USER_REGULAR_B_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + serviceId + ")]").exists());

        mockMvc.perform(get("/api/services/" + serviceId)
                        .with(httpBasic(USER_REGULAR_B_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Auth Service B"));

        mockMvc.perform(get("/api/services")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + serviceId + ")]").doesNotExist());

        mockMvc.perform(get("/api/services/" + serviceId)
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNotFound());

        String updatePayload = """
                {
                    "name": "Hijacked Auth Service",
                    "description": "Attempted takeover",
                    "endpointUrl": "https://hijacked.spectra.local",
                    "environment": "STAGING"
                }
                """;

        mockMvc.perform(put("/api/services/" + serviceId)
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/services/" + serviceId + "/status?active=false")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void sameServiceName_AllowedAcrossDifferentWorkspaces() throws Exception {
        String commonPayload = """
                {
                    "name": "Billing Gateway",
                    "description": "Common service name",
                    "endpointUrl": "https://billing.spectra.local",
                    "environment": "PRODUCTION",
                    "active": true
                }
                """;

        MvcResult resultB = mockMvc.perform(post("/api/services")
                        .with(httpBasic(USER_REGULAR_B_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commonPayload))
                .andExpect(status().isCreated())
                .andReturn();

        Number idBNum = JsonPath.read(resultB.getResponse().getContentAsString(), "$.id");
        Long idB = idBNum.longValue();

        mockMvc.perform(post("/api/services")
                        .with(httpBasic(USER_REGULAR_B_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commonPayload))
                .andExpect(status().isConflict());

        MvcResult resultC = mockMvc.perform(post("/api/services")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commonPayload))
                .andExpect(status().isCreated())
                .andReturn();

        Number idCNum = JsonPath.read(resultC.getResponse().getContentAsString(), "$.id");
        Long idC = idCNum.longValue();

        assertNotEquals(idB, idC);
    }

    @Test
    void metricsAlertsAndIncidents_IsolatedBetweenRegularUsers() throws Exception {
        String servicePayload = """
                {
                    "name": "Inventory Service B",
                    "description": "User B Inventory",
                    "endpointUrl": "https://inv-b.spectra.local",
                    "environment": "PRODUCTION",
                    "active": true
                }
                """;

        MvcResult svcResult = mockMvc.perform(post("/api/services")
                        .with(httpBasic(USER_REGULAR_B_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(servicePayload))
                .andExpect(status().isCreated())
                .andReturn();

        Number serviceIdNum = JsonPath.read(svcResult.getResponse().getContentAsString(), "$.id");
        Long serviceId = serviceIdNum.longValue();

        String rulePayload = """
                {
                    "serviceId": %d,
                    "ruleName": "High CPU Rule B",
                    "metricName": "CPU_USAGE",
                    "unit": "PERCENT",
                    "comparator": "GREATER_THAN",
                    "thresholdValue": 80.0,
                    "severity": "CRITICAL"
                }
                """.formatted(serviceId);

        MvcResult ruleResult = mockMvc.perform(post("/api/alert-rules")
                        .with(httpBasic(USER_REGULAR_B_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rulePayload))
                .andExpect(status().isCreated())
                .andReturn();

        Number ruleIdNum = JsonPath.read(ruleResult.getResponse().getContentAsString(), "$.id");
        Long ruleId = ruleIdNum.longValue();

        String metricPayload = """
                {
                    "serviceId": %d,
                    "metricName": "CPU_USAGE",
                    "metricValue": 92.5,
                    "unit": "PERCENT"
                }
                """.formatted(serviceId);

        mockMvc.perform(post("/api/metrics")
                        .with(httpBasic(USER_REGULAR_B_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metricPayload))
                .andExpect(status().isCreated());

        MvcResult alertListResult = mockMvc.perform(get("/api/alerts")
                        .with(httpBasic(USER_REGULAR_B_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andReturn();

        Number alertIdNum = JsonPath.read(alertListResult.getResponse().getContentAsString(), "$[0].id");
        Long alertId = alertIdNum.longValue();

        String incPayload = """
                {
                    "serviceId": %d,
                    "title": "CPU Spike Incident B",
                    "description": "High CPU reported",
                    "severity": "CRITICAL",
                    "author": "%s"
                }
                """.formatted(serviceId, USER_REGULAR_B_EMAIL);

        MvcResult incResult = mockMvc.perform(post("/api/incidents")
                        .with(httpBasic(USER_REGULAR_B_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incPayload))
                .andExpect(status().isCreated())
                .andReturn();

        Number incIdNum = JsonPath.read(incResult.getResponse().getContentAsString(), "$.id");
        Long incidentId = incIdNum.longValue();

        mockMvc.perform(post("/api/incidents/" + incidentId + "/notes")
                        .with(httpBasic(USER_REGULAR_B_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "author": "%s",
                            "noteText": "Diagnostic note by B"
                        }
                        """.formatted(USER_REGULAR_B_EMAIL)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/services/" + serviceId)
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/metrics")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metricPayload))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/metrics/recent")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/metrics/service/" + serviceId)
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/alert-rules")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/alert-rules/" + ruleId)
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/alert-rules/service/" + serviceId)
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/alerts")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/alerts/" + alertId)
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/alerts/" + alertId + "/acknowledge")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/incidents")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/incidents/" + incidentId)
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/incidents/" + incidentId + "/notes")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/incidents/" + incidentId + "/notes")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "author": "%s",
                            "noteText": "Cross workspace note attack"
                        }
                        """.formatted(USER_REGULAR_C_EMAIL)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "status": "INVESTIGATING",
                            "author": "%s"
                        }
                        """.formatted(USER_REGULAR_C_EMAIL)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCentralizedMonitoring_CanReadForeignData_CannotMutateForeignData() throws Exception {
        String servicePayload = """
                {
                    "name": "Order Engine C",
                    "description": "User C Order Engine",
                    "endpointUrl": "https://orders-c.spectra.local",
                    "environment": "PRODUCTION",
                    "active": true
                }
                """;

        MvcResult svcResult = mockMvc.perform(post("/api/services")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(servicePayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerEmail").value(USER_REGULAR_C_EMAIL))
                .andReturn();

        Number serviceIdNum = JsonPath.read(svcResult.getResponse().getContentAsString(), "$.id");
        Long serviceId = serviceIdNum.longValue();

        String rulePayload = """
                {
                    "serviceId": %d,
                    "ruleName": "Order Engine Latency Rule",
                    "metricName": "RESPONSE_TIME",
                    "unit": "MILLISECONDS",
                    "comparator": "GREATER_THAN",
                    "thresholdValue": 200.0,
                    "severity": "HIGH"
                }
                """.formatted(serviceId);

        MvcResult ruleResult = mockMvc.perform(post("/api/alert-rules")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rulePayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerEmail").value(USER_REGULAR_C_EMAIL))
                .andReturn();

        Number ruleIdNum = JsonPath.read(ruleResult.getResponse().getContentAsString(), "$.id");
        Long ruleId = ruleIdNum.longValue();

        String metricPayload = """
                {
                    "serviceId": %d,
                    "metricName": "RESPONSE_TIME",
                    "metricValue": 350.0,
                    "unit": "MILLISECONDS"
                }
                """.formatted(serviceId);

        mockMvc.perform(post("/api/metrics")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metricPayload))
                .andExpect(status().isCreated());

        MvcResult alertListResult = mockMvc.perform(get("/api/alerts")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andReturn();

        Number alertIdNum = JsonPath.read(alertListResult.getResponse().getContentAsString(), "$[0].id");
        Long alertId = alertIdNum.longValue();

        String incPayload = """
                {
                    "serviceId": %d,
                    "title": "Order Engine Response Spike",
                    "description": "High latency on order processing",
                    "severity": "HIGH",
                    "author": "%s"
                }
                """.formatted(serviceId, USER_REGULAR_C_EMAIL);

        MvcResult incResult = mockMvc.perform(post("/api/incidents")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerEmail").value(USER_REGULAR_C_EMAIL))
                .andReturn();

        Number incIdNum = JsonPath.read(incResult.getResponse().getContentAsString(), "$.id");
        Long incidentId = incIdNum.longValue();

        mockMvc.perform(post("/api/incidents/" + incidentId + "/notes")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "author": "%s",
                            "noteText": "Investigating order db query plan"
                        }
                        """.formatted(USER_REGULAR_C_EMAIL)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/services")
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + serviceId + ")].ownerEmail").value(USER_REGULAR_C_EMAIL));

        mockMvc.perform(get("/api/services/" + serviceId)
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(serviceId))
                .andExpect(jsonPath("$.name").value("Order Engine C"))
                .andExpect(jsonPath("$.ownerEmail").value(USER_REGULAR_C_EMAIL));

        mockMvc.perform(get("/api/metrics/recent")
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.serviceId == " + serviceId + ")]").exists());

        mockMvc.perform(get("/api/metrics/service/" + serviceId)
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].metricValue").value(350.0));

        mockMvc.perform(get("/api/alert-rules")
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + ruleId + ")].ownerEmail").value(USER_REGULAR_C_EMAIL));

        mockMvc.perform(get("/api/alert-rules/" + ruleId)
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ruleId))
                .andExpect(jsonPath("$.ruleName").value("Order Engine Latency Rule"));

        mockMvc.perform(get("/api/alert-rules/service/" + serviceId)
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/alerts")
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + alertId + ")].ownerEmail").value(USER_REGULAR_C_EMAIL));

        mockMvc.perform(get("/api/alerts/" + alertId)
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alertId));

        mockMvc.perform(get("/api/alerts/service/" + serviceId)
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/incidents")
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + incidentId + ")].ownerEmail").value(USER_REGULAR_C_EMAIL));

        mockMvc.perform(get("/api/incidents/" + incidentId)
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(incidentId));

        mockMvc.perform(get("/api/incidents/" + incidentId + "/notes")
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(put("/api/services/" + serviceId)
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "name": "Admin Tampered Service",
                            "endpointUrl": "https://tampered.example.com",
                            "environment": "STAGING"
                        }
                        """))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/services/" + serviceId + "/status?active=false")
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/metrics")
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metricPayload))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/alert-rules")
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "serviceId": %d,
                            "ruleName": "Admin Injected Rule",
                            "metricName": "CPU_USAGE",
                            "unit": "PERCENT",
                            "comparator": "GREATER_THAN",
                            "thresholdValue": 90.0,
                            "severity": "CRITICAL"
                        }
                        """.formatted(serviceId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/alert-rules/" + ruleId)
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "ruleName": "Admin Tampered Rule",
                            "metricName": "RESPONSE_TIME",
                            "unit": "MILLISECONDS",
                            "comparator": "GREATER_THAN",
                            "thresholdValue": 500.0,
                            "severity": "CRITICAL"
                        }
                        """))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/alert-rules/" + ruleId + "/status?active=false")
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/alert-rules/" + ruleId)
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/alerts/" + alertId + "/acknowledge")
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/incidents")
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "serviceId": %d,
                            "title": "Admin Foreign Incident",
                            "severity": "HIGH",
                            "author": "%s"
                        }
                        """.formatted(serviceId, USER_ADMIN_EMAIL)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/incidents")
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "alertId": %d,
                            "title": "Admin Foreign Alert Incident",
                            "author": "%s"
                        }
                        """.formatted(alertId, USER_ADMIN_EMAIL)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "status": "INVESTIGATING",
                            "author": "%s"
                        }
                        """.formatted(USER_ADMIN_EMAIL)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/incidents/" + incidentId + "/notes")
                        .with(httpBasic(USER_ADMIN_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "author": "%s",
                            "noteText": "Admin attempted note"
                        }
                        """.formatted(USER_ADMIN_EMAIL)))
                .andExpect(status().isNotFound());
    }

    @Test
    void newlyRegisteredUser_StartsCompletelyEmpty() throws Exception {
        mockMvc.perform(get("/api/services")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/metrics/recent")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/alert-rules")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/alerts")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/incidents")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void userRole_PermittedOperations_InOwnIsolatedWorkspace() throws Exception {
        String createPayload = """
                {
                    "name": "User C Microservice",
                    "description": "User C isolated service",
                    "endpointUrl": "https://service-c.spectra.local",
                    "environment": "STAGING",
                    "active": true
                }
                """;

        MvcResult svcResult = mockMvc.perform(post("/api/services")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("User C Microservice"))
                .andReturn();

        Number svcIdNum = JsonPath.read(svcResult.getResponse().getContentAsString(), "$.id");
        Long serviceId = svcIdNum.longValue();

        String updatePayload = """
                {
                    "name": "User C Microservice Updated",
                    "description": "Updated description",
                    "endpointUrl": "https://service-c-updated.spectra.local",
                    "environment": "PRODUCTION"
                }
                """;

        mockMvc.perform(put("/api/services/" + serviceId)
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("User C Microservice Updated"));

        mockMvc.perform(patch("/api/services/" + serviceId + "/status?active=false")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        String metricPayload = """
                {
                    "serviceId": %d,
                    "metricName": "RESPONSE_TIME",
                    "metricValue": 150.0,
                    "unit": "MILLISECONDS"
                }
                """.formatted(serviceId);

        mockMvc.perform(post("/api/metrics")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(metricPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.metricName").value("RESPONSE_TIME"));

        mockMvc.perform(get("/api/metrics/recent")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        String incPayload = """
                {
                    "serviceId": %d,
                    "title": "Degraded Latency in Service C",
                    "description": "High response time observed",
                    "severity": "MEDIUM",
                    "author": "%s"
                }
                """.formatted(serviceId, USER_REGULAR_C_EMAIL);

        MvcResult incResult = mockMvc.perform(post("/api/incidents")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(incPayload))
                .andExpect(status().isCreated())
                .andReturn();

        Number incIdNum = JsonPath.read(incResult.getResponse().getContentAsString(), "$.id");
        Long incidentId = incIdNum.longValue();

        mockMvc.perform(post("/api/incidents/" + incidentId + "/notes")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "author": "%s",
                            "noteText": "Diagnosing response time spike."
                        }
                        """.formatted(USER_REGULAR_C_EMAIL)))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/incidents/" + incidentId + "/status")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "status": "INVESTIGATING",
                            "author": "%s"
                        }
                        """.formatted(USER_REGULAR_C_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVESTIGATING"));

        MvcResult ruleResult = mockMvc.perform(post("/api/alert-rules")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "serviceId": %d,
                            "ruleName": "User C Permitted Rule",
                            "metricName": "CPU_USAGE",
                            "unit": "PERCENT",
                            "comparator": "GREATER_THAN",
                            "thresholdValue": 80.0,
                            "severity": "HIGH"
                        }
                        """.formatted(serviceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ruleName").value("User C Permitted Rule"))
                .andReturn();

        Number userCRuleIdNum = JsonPath.read(ruleResult.getResponse().getContentAsString(), "$.id");
        Long userCRuleId = userCRuleIdNum.longValue();

        mockMvc.perform(patch("/api/alert-rules/" + userCRuleId + "/status?active=false")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/alert-rules")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "serviceId": 999999,
                            "ruleName": "User C Foreign Service Rule",
                            "metricName": "CPU_USAGE",
                            "unit": "PERCENT",
                            "comparator": "GREATER_THAN",
                            "thresholdValue": 80.0,
                            "severity": "HIGH"
                        }
                        """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/alert-rules/" + userCRuleId)
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNoContent());
    }

    @Test
    void userRole_CreateService_BelongsToCreatingUser_AndCrossWorkspaceAccessDenied() throws Exception {
        String serviceName = "Regress Isolated Service " + System.currentTimeMillis();
        String createPayload = """
                {
                    "name": "%s",
                    "description": "User created regression service",
                    "endpointUrl": "https://regress.spectra.local",
                    "environment": "PRODUCTION",
                    "active": true
                }
                """.formatted(serviceName);

        MvcResult result = mockMvc.perform(post("/api/services")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value(serviceName))
                .andExpect(jsonPath("$.ownerEmail").value(USER_REGULAR_C_EMAIL))
                .andReturn();

        Number idNum = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        Long serviceId = idNum.longValue();

        MonitoredService serviceEntity = serviceRepository.findById(serviceId).orElseThrow();
        assertNotNull(serviceEntity.getUser());
        assertEquals(userC.getId(), serviceEntity.getUser().getId());

        mockMvc.perform(get("/api/services/" + serviceId)
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(serviceId));

        mockMvc.perform(get("/api/services/" + serviceId)
                        .with(httpBasic(USER_REGULAR_B_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/services/" + serviceId)
                        .with(httpBasic(USER_REGULAR_B_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "name": "Tampered Service Name",
                            "endpointUrl": "https://tampered.example.com",
                            "environment": "STAGING"
                        }
                        """))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/services/" + serviceId + "/status?active=false")
                        .with(httpBasic(USER_REGULAR_B_EMAIL, RAW_PASSWORD)))
                .andExpect(status().isNotFound());

        String spoofedPayload = """
                {
                    "name": "%s",
                    "description": "Spoofed owner test",
                    "endpointUrl": "https://spoof.spectra.local",
                    "environment": "DEVELOPMENT",
                    "active": true,
                    "userId": %d,
                    "ownerId": %d
                }
                """.formatted("Spoofed Service " + System.currentTimeMillis(), adminUser.getId(), adminUser.getId());

        MvcResult spoofResult = mockMvc.perform(post("/api/services")
                        .with(httpBasic(USER_REGULAR_C_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(spoofedPayload))
                .andExpect(status().isCreated())
                .andReturn();

        Number spoofIdNum = JsonPath.read(spoofResult.getResponse().getContentAsString(), "$.id");
        Long spoofServiceId = spoofIdNum.longValue();
        MonitoredService spoofEntity = serviceRepository.findById(spoofServiceId).orElseThrow();
        assertNotNull(spoofEntity.getUser());
        assertEquals(userC.getId(), spoofEntity.getUser().getId());
    }
}
