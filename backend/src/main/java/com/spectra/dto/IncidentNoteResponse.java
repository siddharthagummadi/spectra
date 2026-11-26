package com.spectra.dto;

import java.time.Instant;

import com.spectra.entity.IncidentNote;

public record IncidentNoteResponse(
        Long id,
        Long incidentId,
        String author,
        String noteText,
        Instant createdAt) {

    public static IncidentNoteResponse from(IncidentNote note) {
        return new IncidentNoteResponse(
                note.getId(),
                note.getIncident().getId(),
                note.getAuthor(),
                note.getNoteText(),
                note.getCreatedAt());
    }
}
