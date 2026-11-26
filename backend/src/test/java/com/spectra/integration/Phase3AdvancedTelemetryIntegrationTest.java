package com.spectra.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import com.spectra.entity.AnomalyEvent;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.ServiceMetric;
import com.spectra.entity.User;
import com.spectra.repository.AnomalyEventRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.ServiceMetricRepository;
import com.spectra.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
public class Phase3AdvancedTelemetryIntegrationTest {

    private static final String OP_USER_EMAIL = "telemetry_operator@spectra.local";
    private static final String OTHER_USER_EMAIL = "other_operator@spectra.local";
    private static final String RAW_PASSWORD = "Password123!";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    @Autowired
    private ServiceMetricRepository metricRepository;

    @Autowired
    private AnomalyEventRepository anomalyEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private User operatorUser;
    private User otherUser;
    private MonitoredService targetService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        operatorUser = userRepository.findByEmail(OP_USER_EMAIL).orElseGet(() -> {
            User u = new User();
            u.setEmail(OP_USER_EMAIL);
            u.setFullName("Telemetry Operator");
            u.setPassword(passwordEncoder.encode(RAW_PASSWORD));
            u.setRole(Role.OPERATIONS);
            return userRepository.save(u);
        });

        otherUser = userRepository.findByEmail(OTHER_USER_EMAIL).orElseGet(() -> {
            User u = new User();
            u.setEmail(OTHER_USER_EMAIL);
            u.setFullName("Other Operator");
            u.setPassword(passwordEncoder.encode(RAW_PASSWORD));
            u.setRole(Role.USER);
            return userRepository.save(u);
        });

        targetService = serviceRepository.findByNameIgnoreCase("Payment-Gateway-Live").orElseGet(() -> {
            MonitoredService s = new MonitoredService();
            s.setName("Payment-Gateway-Live");
            s.setDescription("Live Payment Microservice");
            s.setEndpointUrl("https://payment.spectra.internal");
            s.setEnvironment("PRODUCTION");
            s.setActive(true);
            s.setUser(operatorUser);
            return serviceRepository.save(s);
        });
    }

    @Test
    @DisplayName("Phase 3.1: Standard External Single Telemetry Submission")
    void testStandardExternalTelemetrySingle() throws Exception {
        String payload = """
                {
                    "serviceId": %d,
                    "metricName": "CPU_USAGE",
                    "metricValue": 45.5,
                    "unit": "PERCENT"
                }
                """.formatted(targetService.getId());

        mockMvc.perform(post("/api/telemetry/external")
                        .with(httpBasic(OP_USER_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.serviceId").value(targetService.getId()));

        List<ServiceMetric> metrics = metricRepository.findByServiceIdOrderByRecordedAtDescIdDesc(
                targetService.getId(), org.springframework.data.domain.PageRequest.of(0, 10)
        );
        assertFalse(metrics.isEmpty());
        ServiceMetric m = metrics.get(0);
        assertEquals("CPU_USAGE", m.getMetricName());
        assertEquals(45.5, m.getMetricValue());
        assertEquals("PERCENT", m.getUnit());
    }

    @Test
    @DisplayName("Phase 3.2: Standard External Batch Telemetry Submission")
    void testStandardExternalTelemetryBatch() throws Exception {
        String batchPayload = """
                {
                    "serviceName": "Payment-Gateway-Live",
                    "metrics": [
                        { "metricName": "CPU_USAGE", "value": 42.0, "unit": "PERCENT" },
                        { "metricName": "MEMORY_USAGE", "value": 68.5, "unit": "PERCENT" },
                        { "metricName": "RESPONSE_TIME", "value": 115.0, "unit": "MILLISECONDS" },
                        { "metricName": "ERROR_RATE", "value": 0.15, "unit": "PERCENT" }
                    ]
                }
                """;

        mockMvc.perform(post("/api/telemetry/batch")
                        .with(httpBasic(OP_USER_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.totalReceived").value(4))
                .andExpect(jsonPath("$.accepted").value(4))
                .andExpect(jsonPath("$.rejected").value(0));
    }

    @Test
    @DisplayName("Phase 3.3: OpenTelemetry (OTLP) HTTP/JSON Metrics Ingestion")
    void testOpenTelemetryOtlpIngestion() throws Exception {
        long nowNanos = System.currentTimeMillis() * 1_000_000L;
        String otlpPayload = """
                {
                  "resourceMetrics": [
                    {
                      "resource": {
                        "attributes": [
                          { "key": "service.name", "value": { "stringValue": "Payment-Gateway-Live" } },
                          { "key": "host.name", "value": { "stringValue": "k8s-node-04" } }
                        ]
                      },
                      "scopeMetrics": [
                        {
                          "scope": { "name": "io.opentelemetry.jvm" },
                          "metrics": [
                            {
                              "name": "process.cpu.utilization",
                              "unit": "1",
                              "gauge": {
                                "dataPoints": [
                                  {
                                    "timeUnixNano": %d,
                                    "asDouble": 0.52
                                  }
                                ]
                              }
                            },
                            {
                              "name": "http.server.duration",
                              "unit": "s",
                              "gauge": {
                                "dataPoints": [
                                  {
                                    "timeUnixNano": %d,
                                    "asDouble": 0.125
                                  }
                                ]
                              }
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """.formatted(nowNanos, nowNanos);

        mockMvc.perform(post("/api/telemetry/otlp/v1/metrics")
                        .with(httpBasic(OP_USER_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(otlpPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.totalReceived").value(2))
                .andExpect(jsonPath("$.accepted").value(2));

        // Verify that OTLP metric name mappings & unit scalings executed correctly:
        // process.cpu.utilization (0.52 ratio) -> CPU_USAGE (52.0%)
        // http.server.duration (0.125 s) -> RESPONSE_TIME (125.0 ms)
        List<ServiceMetric> recent = metricRepository.findByServiceIdOrderByRecordedAtDescIdDesc(
                targetService.getId(), org.springframework.data.domain.PageRequest.of(0, 10)
        );
        assertTrue(recent.stream().anyMatch(m -> "CPU_USAGE".equals(m.getMetricName()) && Math.abs(m.getMetricValue() - 52.0) < 0.01));
        assertTrue(recent.stream().anyMatch(m -> "RESPONSE_TIME".equals(m.getMetricName()) && Math.abs(m.getMetricValue() - 125.0) < 0.01));
    }

    @Test
    @DisplayName("Phase 3.4: Prometheus-Compatible Text Exposition Ingestion")
    void testPrometheusTextIngestion() throws Exception {
        long nowMillis = System.currentTimeMillis();
        String prometheusPayload = """
                # HELP process_cpu_usage Process CPU usage percentage
                # TYPE process_cpu_usage gauge
                process_cpu_usage{service="Payment-Gateway-Live",env="prod"} 48.2 %d
                
                # HELP http_requests_duration_milliseconds Latency in ms
                # TYPE http_requests_duration_milliseconds gauge
                http_requests_duration_milliseconds{service="Payment-Gateway-Live",env="prod"} 140.5 %d
                
                # HELP jvm_memory_used_percent Memory percentage
                # TYPE jvm_memory_used_percent gauge
                jvm_memory_used_percent{service="Payment-Gateway-Live",env="prod"} 62.0 %d
                """.formatted(nowMillis, nowMillis, nowMillis);

        mockMvc.perform(post("/api/telemetry/prometheus")
                        .with(httpBasic(OP_USER_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(prometheusPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.totalReceived").value(3))
                .andExpect(jsonPath("$.accepted").value(3));

        List<ServiceMetric> recent = metricRepository.findByServiceIdOrderByRecordedAtDescIdDesc(
                targetService.getId(), org.springframework.data.domain.PageRequest.of(0, 10)
        );
        assertTrue(recent.stream().anyMatch(m -> "CPU_USAGE".equals(m.getMetricName()) && Math.abs(m.getMetricValue() - 48.2) < 0.01));
        assertTrue(recent.stream().anyMatch(m -> "RESPONSE_TIME".equals(m.getMetricName()) && Math.abs(m.getMetricValue() - 140.5) < 0.01));
        assertTrue(recent.stream().anyMatch(m -> "MEMORY_USAGE".equals(m.getMetricName()) && Math.abs(m.getMetricValue() - 62.0) < 0.01));
    }

    @Test
    @DisplayName("Phase 3.5: Anomaly Detection Integration from External Telemetry")
    void testDetectionPipelineIntegrationFromExternalTelemetry() throws Exception {
        // Feed 15 normal baseline observations so that statistical anomaly detector has history
        for (int i = 0; i < 15; i++) {
            String baselinePayload = """
                    {
                        "serviceId": %d,
                        "metricName": "CPU_USAGE",
                        "metricValue": %.1f,
                        "unit": "PERCENT"
                    }
                    """.formatted(targetService.getId(), 40.0 + (i % 3));

            mockMvc.perform(post("/api/telemetry/external")
                            .with(httpBasic(OP_USER_EMAIL, RAW_PASSWORD))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(baselinePayload))
                    .andExpect(status().isCreated());
        }

        // Now inject an extreme anomalous spike via external telemetry (CPU = 98.5%)
        String anomalyPayload = """
                {
                    "serviceId": %d,
                    "metricName": "CPU_USAGE",
                    "metricValue": 98.5,
                    "unit": "PERCENT"
                }
                """.formatted(targetService.getId());

        mockMvc.perform(post("/api/telemetry/external")
                        .with(httpBasic(OP_USER_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(anomalyPayload))
                .andExpect(status().isCreated());

        // Verify that the anomaly detection engine triggered and persisted an AnomalyEvent
        List<AnomalyEvent> anomalies = anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(
                targetService.getId(), org.springframework.data.domain.PageRequest.of(0, 10)
        );
        assertFalse(anomalies.isEmpty(), "External anomaly injection must trigger an AnomalyEvent in the database");
        AnomalyEvent event = anomalies.get(0);
        assertEquals("CPU_USAGE", event.getMetricName());
        assertEquals(98.5, event.getObservedValue());
        assertEquals("POSITIVE_SPIKE", event.getDirection());
    }

    @Test
    @DisplayName("Phase 3.6: Safety & Validation on Invalid External Payloads")
    void testInvalidPayloadHandling() throws Exception {
        // 1. Missing service ID and unknown service name
        String unknownService = """
                {
                    "serviceName": "NonExistentServiceXYZ",
                    "metricName": "CPU_USAGE",
                    "metricValue": 50.0
                }
                """;
        mockMvc.perform(post("/api/telemetry/external")
                        .with(httpBasic(OP_USER_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownService))
                .andExpect(status().isNotFound());

        // 2. Corrupt/Empty OTLP payload
        mockMvc.perform(post("/api/telemetry/otlp/v1/metrics")
                        .with(httpBasic(OP_USER_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        // 3. Out-of-bounds metric value (CPU > 100%)
        String outOfBounds = """
                {
                    "serviceId": %d,
                    "metricName": "CPU_USAGE",
                    "metricValue": 250.0
                }
                """.formatted(targetService.getId());
        mockMvc.perform(post("/api/telemetry/external")
                        .with(httpBasic(OP_USER_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(outOfBounds))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Phase 3.7: Cross-Tenant Telemetry Ingestion Protection")
    void testCrossTenantTelemetryIngestionBlocked() throws Exception {
        // User otherUser (who does not own targetService) attempts to inject telemetry into operatorUser's service
        String crossTenantPayload = """
                {
                    "serviceId": %d,
                    "metricName": "CPU_USAGE",
                    "metricValue": 55.0
                }
                """.formatted(targetService.getId());

        mockMvc.perform(post("/api/telemetry/external")
                        .with(httpBasic(OTHER_USER_EMAIL, RAW_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(crossTenantPayload))
                .andExpect(status().isNotFound());
    }
}
