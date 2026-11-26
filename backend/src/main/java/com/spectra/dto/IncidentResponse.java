package com.spectra.dto;

import java.time.Instant;

import com.spectra.entity.Incident;

public record IncidentResponse(
        Long id,
        Long serviceId,
        String serviceName,
        Long alertId,
        String alertMessage,
        String title,
        String description,
        String severity,
        String status,
        String assignedTo,
        String resolutionSummary,
        Instant triggeredAt,
        Instant resolvedAt,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt,
        String ownerEmail) {

    public static IncidentResponse from(Incident incident) {
        String ownerEmail = (incident.getService() != null && incident.getService().getUser() != null)
                ? incident.getService().getUser().getEmail() : null;
        return new IncidentResponse(
                incident.getId(),
                incident.getService().getId(),
                incident.getService().getName(),
                incident.getAlert() != null ? incident.getAlert().getId() : null,
                incident.getAlert() != null ? incident.getAlert().getMessage() : null,
                incident.getTitle(),
                incident.getDescription(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getAssignedTo(),
                incident.getResolutionSummary(),
                incident.getTriggeredAt(),
                incident.getResolvedAt(),
                incident.getClosedAt(),
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                ownerEmail);
    }
}
