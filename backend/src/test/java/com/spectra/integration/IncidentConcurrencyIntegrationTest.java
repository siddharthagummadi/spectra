package com.spectra.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.spectra.controller.IncidentController;
import com.spectra.dto.CreateIncidentRequest;
import com.spectra.dto.UpdateIncidentStatusRequest;
import com.spectra.entity.AlertEvent;
import com.spectra.entity.AlertRule;
import com.spectra.entity.Incident;
import com.spectra.entity.IncidentNote;
import com.spectra.entity.MonitoredService;
import com.spectra.exception.GlobalExceptionHandler;
import com.spectra.repository.AlertEventRepository;
import com.spectra.repository.AlertRuleRepository;
import com.spectra.repository.IncidentNoteRepository;
import com.spectra.repository.IncidentRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.service.IncidentService;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class IncidentConcurrencyIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private IncidentController incidentController;

    @Autowired
    private IncidentService incidentService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentNoteRepository incidentNoteRepository;

    @Autowired
    private AlertEventRepository alertEventRepository;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private MonitoredServiceRepository serviceRepository;

    private MonitoredService testService;
    private AlertRule testRule;
    private AlertEvent testAlert;
    private final List<Long> testCreatedIncidentIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(incidentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        testService = new MonitoredService();
        testService.setName("Concurrency Test Service");
        testService.setEndpointUrl("https://concurrency.test.com");
        testService.setEnvironment("DEVELOPMENT");
        testService.setActive(true);
        testService = serviceRepository.save(testService);

        testRule = new AlertRule();
        testRule.setService(testService);
        testRule.setRuleName("Concurrency Test Rule");
        testRule.setMetricName("CPU_USAGE");
        testRule.setUnit("PERCENT");
        testRule.setComparator("GREATER_THAN");
        testRule.setThresholdValue(80.0);
        testRule.setSeverity("HIGH");
        testRule.setActive(true);
        testRule = alertRuleRepository.save(testRule);

        testAlert = new AlertEvent();
        testAlert.setService(testService);
        testAlert.setRule(testRule);
        testAlert.setMetricName("CPU_USAGE");
        testAlert.setUnit("PERCENT");
        testAlert.setTriggerValue(95.0);
        testAlert.setThresholdValue(80.0);
        testAlert.setSeverity("HIGH");
        testAlert.setStatus("TRIGGERED");
        testAlert.setMessage("CPU high breach");
        testAlert.setTriggeredAt(Instant.now());
        testAlert = alertEventRepository.save(testAlert);
    }

    @AfterEach
    void tearDown() {
        for (Long incidentId : testCreatedIncidentIds) {
            List<IncidentNote> notes = incidentNoteRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId);
            if (!notes.isEmpty()) {
                incidentNoteRepository.deleteAll(notes);
            }
            if (incidentRepository.existsById(incidentId)) {
                incidentRepository.deleteById(incidentId);
            }
        }
        testCreatedIncidentIds.clear();

        if (testAlert != null && testAlert.getId() != null) {
            alertEventRepository.deleteById(testAlert.getId());
            testAlert = null;
        }
        if (testRule != null && testRule.getId() != null) {
            alertRuleRepository.deleteById(testRule.getId());
            testRule = null;
        }
        if (testService != null && testService.getId() != null) {
            serviceRepository.deleteById(testService.getId());
            testService = null;
        }
    }

    @Test
    void duplicateActiveIncidentForSameAlert_RejectedAndAllowedAfterResolution() {
        CreateIncidentRequest firstRequest = new CreateIncidentRequest();
        firstRequest.setAlertId(testAlert.getId());
        firstRequest.setTitle("Incident 1 for Alert");
        firstRequest.setAuthor("DevOps 1");

        Incident firstIncident = incidentService.createIncident(firstRequest);
        assertNotNull(firstIncident);
        testCreatedIncidentIds.add(firstIncident.getId());
        assertEquals("OPEN", firstIncident.getStatus());

        CreateIncidentRequest duplicateRequest = new CreateIncidentRequest();
        duplicateRequest.setAlertId(testAlert.getId());
        duplicateRequest.setTitle("Incident 2 for Same Alert");
        duplicateRequest.setAuthor("DevOps 2");

        assertThrows(RuntimeException.class, () -> incidentService.createIncident(duplicateRequest));

        UpdateIncidentStatusRequest resolveRequest = new UpdateIncidentStatusRequest();
        resolveRequest.setStatus("RESOLVED");
        resolveRequest.setAuthor("Lead");
        resolveRequest.setResolutionSummary("Mitigated issue");

        Incident resolvedFirst = incidentService.transitionStatus(firstIncident.getId(), resolveRequest);
        assertEquals("RESOLVED", resolvedFirst.getStatus());

        CreateIncidentRequest subsequentRequest = new CreateIncidentRequest();
        subsequentRequest.setAlertId(testAlert.getId());
        subsequentRequest.setTitle("Incident 3 After Resolution");
        subsequentRequest.setAuthor("DevOps 3");

        Incident subsequentIncident = incidentService.createIncident(subsequentRequest);
        assertNotNull(subsequentIncident);
        testCreatedIncidentIds.add(subsequentIncident.getId());
        assertEquals("OPEN", subsequentIncident.getStatus());
    }

    @Test
    void reopenResolvedIncident_WhenAnotherActiveIncidentExists_Returns409AndPreservesState() throws Exception {
        CreateIncidentRequest firstRequest = new CreateIncidentRequest();
        firstRequest.setAlertId(testAlert.getId());
        firstRequest.setTitle("Incident 1 for Alert");
        firstRequest.setAuthor("DevOps 1");

        Incident firstIncident = incidentService.createIncident(firstRequest);
        Long firstId = firstIncident.getId();
        testCreatedIncidentIds.add(firstId);

        UpdateIncidentStatusRequest resolveRequest = new UpdateIncidentStatusRequest();
        resolveRequest.setStatus("RESOLVED");
        resolveRequest.setAuthor("Lead");
        resolveRequest.setResolutionSummary("Issue resolved");
        incidentService.transitionStatus(firstId, resolveRequest);

        Incident resolvedIncident = incidentRepository.findById(firstId).orElseThrow();
        assertEquals("RESOLVED", resolvedIncident.getStatus());
        Instant originalResolvedAt = resolvedIncident.getResolvedAt();
        assertNotNull(originalResolvedAt);

        CreateIncidentRequest secondRequest = new CreateIncidentRequest();
        secondRequest.setAlertId(testAlert.getId());
        secondRequest.setTitle("Incident 2 for Alert");
        secondRequest.setAuthor("DevOps 2");

        Incident secondIncident = incidentService.createIncident(secondRequest);
        Long secondId = secondIncident.getId();
        testCreatedIncidentIds.add(secondId);
        assertEquals("OPEN", secondIncident.getStatus());

        String reopenJson = """
                {
                    "status": "INVESTIGATING",
                    "author": "Operator"
                }
                """;

        mockMvc.perform(patch("/api/incidents/" + firstId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(reopenJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        Incident freshFirst = incidentRepository.findById(firstId).orElseThrow();
        assertEquals("RESOLVED", freshFirst.getStatus());
        assertEquals(originalResolvedAt, freshFirst.getResolvedAt());
        assertEquals("Issue resolved", freshFirst.getResolutionSummary());

        Incident freshSecond = incidentRepository.findById(secondId).orElseThrow();
        assertEquals("OPEN", freshSecond.getStatus());
    }
}
