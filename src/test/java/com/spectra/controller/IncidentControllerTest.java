package com.spectra.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.spectra.dto.CreateIncidentNoteRequest;
import com.spectra.dto.CreateIncidentRequest;
import com.spectra.dto.UpdateIncidentStatusRequest;
import com.spectra.entity.Incident;
import com.spectra.entity.IncidentNote;
import com.spectra.entity.MonitoredService;
import com.spectra.exception.GlobalExceptionHandler;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.service.IncidentService;

@ExtendWith(MockitoExtension.class)
class IncidentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IncidentService service;

    @InjectMocks
    private IncidentController controller;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createIncident_ValidManual_ReturnsCreated() throws Exception {
        MonitoredService serviceEntity = new MonitoredService();
        serviceEntity.setId(1L);
        serviceEntity.setName("Auth Service");

        Incident incident = new Incident();
        incident.setId(10L);
        incident.setService(serviceEntity);
        incident.setTitle("Auth Latency Spike");
        incident.setSeverity("HIGH");
        incident.setStatus("OPEN");
        incident.setTriggeredAt(Instant.parse("2026-10-21T07:00:00Z"));

        when(service.createIncident(any(CreateIncidentRequest.class))).thenReturn(incident);

        String jsonPayload = """
                {
                    "serviceId": 1,
                    "title": "Auth Latency Spike",
                    "severity": "HIGH",
                    "author": "Operator"
                }
                """;

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.title").value("Auth Latency Spike"))
                .andExpect(jsonPath("$.serviceId").value(1))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void createIncident_BlankTitle_ReturnsBadRequest() throws Exception {
        String jsonPayload = """
                {
                    "serviceId": 1,
                    "title": "   ",
                    "severity": "HIGH",
                    "author": "Operator"
                }
                """;

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void createIncident_BlankAuthor_ReturnsBadRequest() throws Exception {
        String jsonPayload = """
                {
                    "serviceId": 1,
                    "title": "Incident Title",
                    "severity": "HIGH",
                    "author": "   "
                }
                """;

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.author").exists());
    }

    @Test
    void getAllIncidents_ReturnsOk() throws Exception {
        MonitoredService serviceEntity = new MonitoredService();
        serviceEntity.setId(1L);
        serviceEntity.setName("Auth Service");

        Incident incident = new Incident();
        incident.setId(10L);
        incident.setService(serviceEntity);
        incident.setTitle("Spike");
        incident.setStatus("OPEN");
        incident.setSeverity("HIGH");

        when(service.getAllIncidents(null, null)).thenReturn(List.of(incident));

        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].title").value("Spike"));
    }

    @Test
    void getIncidentById_ReturnsOk() throws Exception {
        MonitoredService serviceEntity = new MonitoredService();
        serviceEntity.setId(1L);
        serviceEntity.setName("Auth Service");

        Incident incident = new Incident();
        incident.setId(10L);
        incident.setService(serviceEntity);
        incident.setTitle("Spike");
        incident.setStatus("OPEN");
        incident.setSeverity("HIGH");

        when(service.getIncidentById(10L)).thenReturn(incident);

        mockMvc.perform(get("/api/incidents/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void getIncidentById_Nonexistent_ReturnsNotFound() throws Exception {
        when(service.getIncidentById(999L))
                .thenThrow(new ResourceNotFoundException("Incident not found with id: 999"));

        mockMvc.perform(get("/api/incidents/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void transitionStatus_ReturnsOk() throws Exception {
        MonitoredService serviceEntity = new MonitoredService();
        serviceEntity.setId(1L);
        serviceEntity.setName("Auth Service");

        Incident updated = new Incident();
        updated.setId(10L);
        updated.setService(serviceEntity);
        updated.setTitle("Spike");
        updated.setStatus("RESOLVED");
        updated.setSeverity("HIGH");

        when(service.transitionStatus(eq(10L), any(UpdateIncidentStatusRequest.class))).thenReturn(updated);

        String jsonPayload = """
                {
                    "status": "RESOLVED",
                    "author": "Lead",
                    "resolutionSummary": "Fixed database connection pool leak"
                }
                """;

        mockMvc.perform(patch("/api/incidents/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void addNote_Valid_ReturnsCreated() throws Exception {
        Incident incident = new Incident();
        incident.setId(10L);

        IncidentNote note = new IncidentNote();
        note.setId(100L);
        note.setIncident(incident);
        note.setAuthor("Analyst");
        note.setNoteText("Found root cause");
        note.setCreatedAt(Instant.parse("2026-10-21T07:30:00Z"));

        when(service.addNote(eq(10L), any(CreateIncidentNoteRequest.class))).thenReturn(note);

        String jsonPayload = """
                {
                    "author": "Analyst",
                    "noteText": "Found root cause"
                }
                """;

        mockMvc.perform(post("/api/incidents/10/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.author").value("Analyst"))
                .andExpect(jsonPath("$.noteText").value("Found root cause"));
    }

    @Test
    void getNotes_ReturnsOk() throws Exception {
        Incident incident = new Incident();
        incident.setId(10L);

        IncidentNote note = new IncidentNote();
        note.setId(100L);
        note.setIncident(incident);
        note.setAuthor("Analyst");
        note.setNoteText("Found root cause");
        note.setCreatedAt(Instant.parse("2026-10-21T07:30:00Z"));

        when(service.getNotesByIncidentId(10L)).thenReturn(List.of(note));

        mockMvc.perform(get("/api/incidents/10/notes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].author").value("Analyst"));
    }
}
