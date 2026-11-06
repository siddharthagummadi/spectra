package com.spectra.service;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.dto.CreateIncidentNoteRequest;
import com.spectra.dto.CreateIncidentRequest;
import com.spectra.dto.UpdateIncidentStatusRequest;
import com.spectra.entity.AlertEvent;
import com.spectra.entity.Incident;
import com.spectra.entity.IncidentNote;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.AlertEventRepository;
import com.spectra.repository.IncidentNoteRepository;
import com.spectra.repository.IncidentRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.UserRepository;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentNoteRepository noteRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final AlertEventRepository alertEventRepository;
    private final UserRepository userRepository;

    @Autowired
    public IncidentService(
            IncidentRepository incidentRepository,
            IncidentNoteRepository noteRepository,
            MonitoredServiceRepository serviceRepository,
            AlertEventRepository alertEventRepository,
            UserRepository userRepository) {
        this.incidentRepository = incidentRepository;
        this.noteRepository = noteRepository;
        this.serviceRepository = serviceRepository;
        this.alertEventRepository = alertEventRepository;
        this.userRepository = userRepository;
    }

    public IncidentService(
            IncidentRepository incidentRepository,
            IncidentNoteRepository noteRepository,
            MonitoredServiceRepository serviceRepository,
            AlertEventRepository alertEventRepository) {
        this(incidentRepository, noteRepository, serviceRepository, alertEventRepository, null);
    }

    private User getCurrentUser() {
        if (userRepository == null) return null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    private Incident getOwnedIncidentById(Long id, User currentUser) {
        if (currentUser != null) {
            return incidentRepository.findByIdAndServiceUserId(id, currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));
        }
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));
    }

    @Bean
    @Profile("!test")
    public ApplicationRunner incidentIndexInitializer(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_active_incident_per_alert "
                    + "ON incidents (alert_id) "
                    + "WHERE alert_id IS NOT NULL AND status IN ('OPEN', 'INVESTIGATING')"
            );
        };
    }

    @Transactional
    public Incident createIncident(CreateIncidentRequest request) {
        User currentUser = getCurrentUser();
        String author = validateAuthor(request.getAuthor());
        String title = request.getTitle().trim();

        if (request.getAlertId() != null) {
            AlertEvent alert;
            if (currentUser != null) {
                alert = alertEventRepository.findByIdAndServiceUserIdForUpdate(request.getAlertId(), currentUser.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + request.getAlertId()));
            } else {
                alert = alertEventRepository.findByIdForUpdate(request.getAlertId())
                        .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + request.getAlertId()));
            }

            if (incidentRepository.existsByAlertIdAndStatusIn(alert.getId(), List.of("OPEN", "INVESTIGATING"))) {
                throw new IllegalArgumentException("An active incident already exists for alert ID " + alert.getId());
            }

            MonitoredService service = alert.getService();
            if (request.getServiceId() != null && !request.getServiceId().equals(service.getId())) {
                throw new BadRequestException("Supplied service ID " + request.getServiceId()
                        + " does not match linked alert's service ID " + service.getId());
            }

            String severity = alert.getSeverity();
            if (request.getSeverity() != null && !request.getSeverity().trim().isEmpty()) {
                if (!request.getSeverity().trim().equalsIgnoreCase(alert.getSeverity())) {
                    throw new BadRequestException("Supplied severity " + request.getSeverity()
                            + " conflicts with linked alert's severity " + alert.getSeverity());
                }
            }

            Incident incident = new Incident();
            incident.setService(service);
            incident.setAlert(alert);
            incident.setTitle(title);
            incident.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
            incident.setSeverity(severity);
            incident.setStatus("OPEN");
            incident.setAssignedTo(request.getAssignedTo() != null ? request.getAssignedTo().trim() : null);
            incident.setTriggeredAt(alert.getTriggeredAt());

            Incident savedIncident = incidentRepository.save(incident);

            if (alert.getStatus().equals("TRIGGERED")) {
                alert.setStatus("ACKNOWLEDGED");
                alertEventRepository.save(alert);
            }

            createAuditNote(savedIncident, author, "Incident created with status OPEN from Alert #" + alert.getId());
            return savedIncident;
        }

        if (request.getServiceId() == null) {
            throw new BadRequestException("Service ID is required for manual incident creation");
        }

        MonitoredService service;
        if (currentUser != null) {
            service = serviceRepository.findByIdAndUserId(request.getServiceId(), currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + request.getServiceId()));
        } else {
            service = serviceRepository.findById(request.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + request.getServiceId()));
        }

        if (request.getSeverity() == null || request.getSeverity().trim().isEmpty()) {
            throw new BadRequestException("Severity is required for manual incident creation");
        }
        String severity = validateSeverity(request.getSeverity());

        if (incidentRepository.existsByServiceIdAndTitleIgnoreCaseAndStatusIn(service.getId(), title, List.of("OPEN", "INVESTIGATING"))) {
            throw new IllegalArgumentException("An active incident with title '" + title + "' already exists for this service");
        }

        Incident incident = new Incident();
        incident.setService(service);
        incident.setTitle(title);
        incident.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        incident.setSeverity(severity);
        incident.setStatus("OPEN");
        incident.setAssignedTo(request.getAssignedTo() != null ? request.getAssignedTo().trim() : null);
        incident.setTriggeredAt(Instant.now());

        Incident savedIncident = incidentRepository.save(incident);
        createAuditNote(savedIncident, author, "Incident created with status OPEN");
        return savedIncident;
    }

    @Transactional
    public Incident transitionStatus(Long id, UpdateIncidentStatusRequest request) {
        User currentUser = getCurrentUser();
        String author = validateAuthor(request.getAuthor());
        String targetStatus = validateStatus(request.getStatus());

        Incident incident = getOwnedIncidentById(id, currentUser);
        String currentStatus = incident.getStatus();

        if (currentStatus.equals("CLOSED")) {
            throw new BadRequestException("Cannot change status of a CLOSED incident");
        }

        if (targetStatus.equals(currentStatus)) {
            throw new BadRequestException("Incident is already in status " + targetStatus);
        }

        if (targetStatus.equals("INVESTIGATING")) {
            if (currentStatus.equals("OPEN")) {
                incident.setStatus("INVESTIGATING");
                createAuditNote(incident, author, "Status changed from OPEN to INVESTIGATING");
            } else if (currentStatus.equals("RESOLVED")) {
                if (incident.getAlert() != null) {
                    Long alertId = incident.getAlert().getId();
                    if (currentUser != null && !isAdmin(currentUser)) {
                        alertEventRepository.findByIdAndServiceUserIdForUpdate(alertId, currentUser.getId())
                                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + alertId));
                    } else {
                        alertEventRepository.findByIdForUpdate(alertId)
                                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + alertId));
                    }
                    if (incidentRepository.existsByAlertIdAndStatusIn(alertId, List.of("OPEN", "INVESTIGATING"))) {
                        throw new IllegalArgumentException("Cannot reopen incident: an active incident already exists for alert ID " + alertId);
                    }
                }
                incident.setStatus("INVESTIGATING");
                incident.setResolvedAt(null);
                createAuditNote(incident, author, "Incident reopened into INVESTIGATING. Previous resolution cleared.");
            } else {
                throw new BadRequestException("Invalid transition from " + currentStatus + " to INVESTIGATING");
            }
        } else if (targetStatus.equals("RESOLVED")) {
            if (currentStatus.equals("OPEN") || currentStatus.equals("INVESTIGATING")) {
                incident.setStatus("RESOLVED");
                incident.setResolvedAt(Instant.now());
                if (request.getResolutionSummary() != null && !request.getResolutionSummary().trim().isEmpty()) {
                    incident.setResolutionSummary(request.getResolutionSummary().trim());
                }
                String summary = incident.getResolutionSummary() != null ? " Reason: " + incident.getResolutionSummary() : "";
                createAuditNote(incident, author, "Status changed from " + currentStatus + " to RESOLVED." + summary);
            } else {
                throw new BadRequestException("Invalid transition from " + currentStatus + " to RESOLVED");
            }
        } else if (targetStatus.equals("CLOSED")) {
            if (currentStatus.equals("RESOLVED")) {
                incident.setStatus("CLOSED");
                incident.setClosedAt(Instant.now());
                createAuditNote(incident, author, "Incident closed.");
            } else {
                throw new BadRequestException("Incident must be RESOLVED before it can be CLOSED");
            }
        } else {
            throw new BadRequestException("Invalid target status: " + targetStatus);
        }

        return incidentRepository.save(incident);
    }

    @Transactional
    public IncidentNote addNote(Long incidentId, CreateIncidentNoteRequest request) {
        User currentUser = getCurrentUser();
        Incident incident = getOwnedIncidentById(incidentId, currentUser);
        String author = validateAuthor(request.getAuthor());
        String noteText = request.getNoteText().trim();

        if (noteText.isEmpty()) {
            throw new BadRequestException("Note text cannot be empty");
        }

        IncidentNote note = new IncidentNote();
        note.setIncident(incident);
        note.setAuthor(author);
        note.setNoteText(noteText);

        return noteRepository.save(note);
    }

    @Transactional(readOnly = true)
    public Incident getIncidentById(Long id) {
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            if (isAdmin(currentUser)) {
                return incidentRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));
            }
            return incidentRepository.findByIdAndServiceUserId(id, currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));
        }
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<Incident> getAllIncidents(String status, Long serviceId) {
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            if (isAdmin(currentUser)) {
                if (serviceId != null && status != null && !status.trim().isEmpty()) {
                    return incidentRepository.findByServiceIdAndStatusOrderByTriggeredAtDescIdDesc(serviceId, status.trim().toUpperCase());
                }
                if (serviceId != null) {
                    return incidentRepository.findByServiceIdOrderByTriggeredAtDescIdDesc(serviceId);
                }
                if (status != null && !status.trim().isEmpty()) {
                    return incidentRepository.findByStatusOrderByTriggeredAtDescIdDesc(status.trim().toUpperCase());
                }
                return incidentRepository.findAllByOrderByTriggeredAtDescIdDesc();
            }

            if (serviceId != null && status != null && !status.trim().isEmpty()) {
                return incidentRepository.findByServiceIdAndServiceUserIdAndStatusOrderByTriggeredAtDescIdDesc(serviceId, currentUser.getId(), status.trim().toUpperCase());
            }
            if (serviceId != null) {
                return incidentRepository.findByServiceIdAndServiceUserIdOrderByTriggeredAtDescIdDesc(serviceId, currentUser.getId());
            }
            if (status != null && !status.trim().isEmpty()) {
                return incidentRepository.findByServiceUserIdAndStatusOrderByTriggeredAtDescIdDesc(currentUser.getId(), status.trim().toUpperCase());
            }
            return incidentRepository.findByServiceUserIdOrderByTriggeredAtDescIdDesc(currentUser.getId());
        }

        if (serviceId != null && status != null && !status.trim().isEmpty()) {
            return incidentRepository.findByServiceIdAndStatusOrderByTriggeredAtDescIdDesc(serviceId, status.trim().toUpperCase());
        }
        if (serviceId != null) {
            return incidentRepository.findByServiceIdOrderByTriggeredAtDescIdDesc(serviceId);
        }
        if (status != null && !status.trim().isEmpty()) {
            return incidentRepository.findByStatusOrderByTriggeredAtDescIdDesc(status.trim().toUpperCase());
        }
        return incidentRepository.findAllByOrderByTriggeredAtDescIdDesc();
    }

    @Transactional(readOnly = true)
    public List<IncidentNote> getNotesByIncidentId(Long incidentId) {
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            if (isAdmin(currentUser)) {
                if (!incidentRepository.existsById(incidentId)) {
                    throw new ResourceNotFoundException("Incident not found with id: " + incidentId);
                }
                return noteRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId);
            }
            if (!incidentRepository.existsByIdAndServiceUserId(incidentId, currentUser.getId())) {
                throw new ResourceNotFoundException("Incident not found with id: " + incidentId);
            }
            return noteRepository.findByIncidentIdAndIncidentServiceUserIdOrderByCreatedAtAsc(incidentId, currentUser.getId());
        }
        if (!incidentRepository.existsById(incidentId)) {
            throw new ResourceNotFoundException("Incident not found with id: " + incidentId);
        }
        return noteRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId);
    }

    private void createAuditNote(Incident incident, String author, String message) {
        IncidentNote note = new IncidentNote();
        note.setIncident(incident);
        note.setAuthor(author);
        note.setNoteText(message);
        noteRepository.save(note);
    }

    private String validateAuthor(String rawAuthor) {
        if (rawAuthor == null || rawAuthor.trim().isEmpty()) {
            throw new BadRequestException("Author is required");
        }
        return rawAuthor.trim();
    }

    private String validateSeverity(String rawSeverity) {
        String trimmed = rawSeverity.trim().toUpperCase();
        if (!trimmed.equals("LOW")
                && !trimmed.equals("MEDIUM")
                && !trimmed.equals("HIGH")
                && !trimmed.equals("CRITICAL")) {
            throw new BadRequestException("Invalid severity: " + rawSeverity
                    + ". Allowed severities are LOW, MEDIUM, HIGH, CRITICAL");
        }
        return trimmed;
    }

    private String validateStatus(String rawStatus) {
        String trimmed = rawStatus.trim().toUpperCase();
        if (!trimmed.equals("OPEN")
                && !trimmed.equals("INVESTIGATING")
                && !trimmed.equals("RESOLVED")
                && !trimmed.equals("CLOSED")) {
            throw new BadRequestException("Invalid status: " + rawStatus
                    + ". Allowed statuses are OPEN, INVESTIGATING, RESOLVED, CLOSED");
        }
        return trimmed;
    }
}
