package com.spectra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.spectra.dto.CreateIncidentNoteRequest;
import com.spectra.dto.CreateIncidentRequest;
import com.spectra.dto.UpdateIncidentStatusRequest;
import com.spectra.entity.AlertEvent;
import com.spectra.entity.Incident;
import com.spectra.entity.IncidentNote;
import com.spectra.entity.MonitoredService;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.AlertEventRepository;
import com.spectra.repository.IncidentNoteRepository;
import com.spectra.repository.IncidentRepository;
import com.spectra.repository.MonitoredServiceRepository;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentNoteRepository noteRepository;

    @Mock
    private MonitoredServiceRepository serviceRepository;

    @Mock
    private AlertEventRepository alertEventRepository;

    @InjectMocks
    private IncidentService incidentService;

    @Test
    void createIncident_Manual_Success() {
        MonitoredService service = new MonitoredService();
        service.setId(1L);
        service.setName("Payment Service");

        CreateIncidentRequest request = new CreateIncidentRequest();
        request.setServiceId(1L);
        request.setTitle("Payment Gateway Timeout");
        request.setDescription("Transactions failing with HTTP 504");
        request.setSeverity("HIGH");
        request.setAuthor("DevOps Lead");

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(incidentRepository.existsByServiceIdAndTitleIgnoreCaseAndStatusIn(1L, "Payment Gateway Timeout", List.of("OPEN", "INVESTIGATING")))
                .thenReturn(false);
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Incident created = incidentService.createIncident(request);

        assertNotNull(created);
        assertEquals("Payment Gateway Timeout", created.getTitle());
        assertEquals("HIGH", created.getSeverity());
        assertEquals("OPEN", created.getStatus());
        verify(incidentRepository).save(any(Incident.class));
        verify(noteRepository).save(any(IncidentNote.class));
    }

    @Test
    void createIncident_Manual_MissingServiceId_ThrowsBadRequestException() {
        CreateIncidentRequest request = new CreateIncidentRequest();
        request.setTitle("Payment Timeout");
        request.setSeverity("HIGH");
        request.setAuthor("Admin");

        assertThrows(BadRequestException.class, () -> incidentService.createIncident(request));
    }

    @Test
    void createIncident_Manual_DuplicateActiveTitle_ThrowsIllegalArgumentException() {
        MonitoredService service = new MonitoredService();
        service.setId(1L);

        CreateIncidentRequest request = new CreateIncidentRequest();
        request.setServiceId(1L);
        request.setTitle("Existing Issue");
        request.setSeverity("HIGH");
        request.setAuthor("Admin");

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(incidentRepository.existsByServiceIdAndTitleIgnoreCaseAndStatusIn(1L, "Existing Issue", List.of("OPEN", "INVESTIGATING")))
                .thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> incidentService.createIncident(request));
    }

    @Test
    void createIncident_WithAlert_LinksAndAcknowledgesAlert() {
        MonitoredService service = new MonitoredService();
        service.setId(1L);

        AlertEvent alert = new AlertEvent();
        alert.setId(10L);
        alert.setService(service);
        alert.setSeverity("CRITICAL");
        alert.setStatus("TRIGGERED");
        alert.setTriggeredAt(Instant.parse("2026-10-21T07:00:00Z"));

        CreateIncidentRequest request = new CreateIncidentRequest();
        request.setAlertId(10L);
        request.setTitle("High CPU on Payment Service");
        request.setAuthor("SRE Engineer");

        when(alertEventRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(alert));
        when(incidentRepository.existsByAlertIdAndStatusIn(10L, List.of("OPEN", "INVESTIGATING"))).thenReturn(false);
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Incident created = incidentService.createIncident(request);

        assertNotNull(created);
        assertEquals("CRITICAL", created.getSeverity());
        assertEquals("OPEN", created.getStatus());
        assertEquals(10L, created.getAlert().getId());
        assertEquals("ACKNOWLEDGED", alert.getStatus());
        verify(alertEventRepository).save(alert);
    }

    @Test
    void createIncident_WithAlert_ServiceMismatch_ThrowsBadRequestException() {
        MonitoredService service = new MonitoredService();
        service.setId(1L);

        AlertEvent alert = new AlertEvent();
        alert.setId(10L);
        alert.setService(service);
        alert.setSeverity("HIGH");

        CreateIncidentRequest request = new CreateIncidentRequest();
        request.setAlertId(10L);
        request.setServiceId(2L);
        request.setTitle("Mismatch Title");
        request.setAuthor("SRE");

        when(alertEventRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(alert));
        when(incidentRepository.existsByAlertIdAndStatusIn(10L, List.of("OPEN", "INVESTIGATING"))).thenReturn(false);

        assertThrows(BadRequestException.class, () -> incidentService.createIncident(request));
    }

    @Test
    void createIncident_WithAlert_SeverityConflict_ThrowsBadRequestException() {
        MonitoredService service = new MonitoredService();
        service.setId(1L);

        AlertEvent alert = new AlertEvent();
        alert.setId(10L);
        alert.setService(service);
        alert.setSeverity("HIGH");

        CreateIncidentRequest request = new CreateIncidentRequest();
        request.setAlertId(10L);
        request.setSeverity("LOW");
        request.setTitle("Conflict Title");
        request.setAuthor("SRE");

        when(alertEventRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(alert));
        when(incidentRepository.existsByAlertIdAndStatusIn(10L, List.of("OPEN", "INVESTIGATING"))).thenReturn(false);

        assertThrows(BadRequestException.class, () -> incidentService.createIncident(request));
    }

    @Test
    void createIncident_WithAlert_DuplicateActiveAlert_ThrowsIllegalArgumentException() {
        AlertEvent alert = new AlertEvent();
        alert.setId(10L);

        CreateIncidentRequest request = new CreateIncidentRequest();
        request.setAlertId(10L);
        request.setTitle("Duplicate Alert Incident");
        request.setAuthor("SRE");

        when(alertEventRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(alert));
        when(incidentRepository.existsByAlertIdAndStatusIn(10L, List.of("OPEN", "INVESTIGATING"))).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> incidentService.createIncident(request));
    }

    @Test
    void transitionStatus_OpenToInvestigating_Success() {
        Incident incident = new Incident();
        incident.setId(1L);
        incident.setStatus("OPEN");

        UpdateIncidentStatusRequest request = new UpdateIncidentStatusRequest();
        request.setStatus("INVESTIGATING");
        request.setAuthor("OnCall Engineer");

        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Incident updated = incidentService.transitionStatus(1L, request);

        assertEquals("INVESTIGATING", updated.getStatus());
        assertNull(updated.getResolvedAt());
        verify(noteRepository).save(any(IncidentNote.class));
    }

    @Test
    void transitionStatus_InvestigatingToResolved_SuccessWithSummary() {
        Incident incident = new Incident();
        incident.setId(1L);
        incident.setStatus("INVESTIGATING");

        UpdateIncidentStatusRequest request = new UpdateIncidentStatusRequest();
        request.setStatus("RESOLVED");
        request.setAuthor("Lead");
        request.setResolutionSummary("Restarted JVM service");

        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Incident updated = incidentService.transitionStatus(1L, request);

        assertEquals("RESOLVED", updated.getStatus());
        assertNotNull(updated.getResolvedAt());
        assertEquals("Restarted JVM service", updated.getResolutionSummary());
    }

    @Test
    void transitionStatus_ResolvedToInvestigating_ClearsResolvedAt() {
        Incident incident = new Incident();
        incident.setId(1L);
        incident.setStatus("RESOLVED");
        incident.setResolvedAt(Instant.now());
        incident.setTriggeredAt(Instant.parse("2026-10-21T06:00:00Z"));

        UpdateIncidentStatusRequest request = new UpdateIncidentStatusRequest();
        request.setStatus("INVESTIGATING");
        request.setAuthor("Lead");

        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Incident updated = incidentService.transitionStatus(1L, request);

        assertEquals("INVESTIGATING", updated.getStatus());
        assertNull(updated.getResolvedAt());
        assertEquals(Instant.parse("2026-10-21T06:00:00Z"), updated.getTriggeredAt());
    }

    @Test
    void transitionStatus_ResolvedToClosed_SetsClosedAt() {
        Incident incident = new Incident();
        incident.setId(1L);
        incident.setStatus("RESOLVED");

        UpdateIncidentStatusRequest request = new UpdateIncidentStatusRequest();
        request.setStatus("CLOSED");
        request.setAuthor("Manager");

        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Incident updated = incidentService.transitionStatus(1L, request);

        assertEquals("CLOSED", updated.getStatus());
        assertNotNull(updated.getClosedAt());
    }

    @Test
    void transitionStatus_OpenToClosed_ThrowsBadRequestException() {
        Incident incident = new Incident();
        incident.setId(1L);
        incident.setStatus("OPEN");

        UpdateIncidentStatusRequest request = new UpdateIncidentStatusRequest();
        request.setStatus("CLOSED");
        request.setAuthor("User");

        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        assertThrows(BadRequestException.class, () -> incidentService.transitionStatus(1L, request));
    }

    @Test
    void transitionStatus_ClosedToAny_ThrowsBadRequestException() {
        Incident incident = new Incident();
        incident.setId(1L);
        incident.setStatus("CLOSED");

        UpdateIncidentStatusRequest request = new UpdateIncidentStatusRequest();
        request.setStatus("INVESTIGATING");
        request.setAuthor("User");

        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        assertThrows(BadRequestException.class, () -> incidentService.transitionStatus(1L, request));
    }

    @Test
    void addNote_Success() {
        Incident incident = new Incident();
        incident.setId(1L);

        CreateIncidentNoteRequest request = new CreateIncidentNoteRequest();
        request.setAuthor("Analyst");
        request.setNoteText("Examined thread dump, deadlock observed");

        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(noteRepository.save(any(IncidentNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IncidentNote note = incidentService.addNote(1L, request);

        assertNotNull(note);
        assertEquals("Analyst", note.getAuthor());
        assertEquals("Examined thread dump, deadlock observed", note.getNoteText());
    }
}
