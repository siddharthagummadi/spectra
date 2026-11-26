package com.spectra.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spectra.dto.CreateIncidentNoteRequest;
import com.spectra.dto.CreateIncidentRequest;
import com.spectra.dto.IncidentAiAnalysisResponse;
import com.spectra.dto.IncidentAiDiagnosticsRequest;
import com.spectra.dto.IncidentAiDiagnosticsResponse;
import com.spectra.dto.IncidentNoteResponse;
import com.spectra.dto.IncidentResponse;
import com.spectra.dto.UpdateIncidentStatusRequest;
import com.spectra.entity.Incident;
import com.spectra.entity.IncidentAiAnalysis;
import com.spectra.entity.IncidentNote;
import com.spectra.service.IncidentAiService;
import com.spectra.service.IncidentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/incidents")
@Validated
public class IncidentController {

    private final IncidentService service;
    private final IncidentAiService aiService;

    @org.springframework.beans.factory.annotation.Autowired
    public IncidentController(IncidentService service, IncidentAiService aiService) {
        this.service = service;
        this.aiService = aiService;
    }

    public IncidentController(IncidentService service) {
        this(service, null);
    }

    @PostMapping
    public ResponseEntity<IncidentResponse> createIncident(@Valid @RequestBody CreateIncidentRequest request) {
        Incident created = service.createIncident(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(IncidentResponse.from(created));
    }

    @GetMapping
    public ResponseEntity<List<IncidentResponse>> getAllIncidents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long serviceId) {
        List<IncidentResponse> incidents = service.getAllIncidents(status, serviceId)
                .stream()
                .map(IncidentResponse::from)
                .toList();
        return ResponseEntity.ok(incidents);
    }

    @GetMapping("/service/{serviceId}")
    public ResponseEntity<List<IncidentResponse>> getIncidentsByServiceId(@PathVariable Long serviceId) {
        List<IncidentResponse> incidents = service.getAllIncidents(null, serviceId)
                .stream()
                .map(IncidentResponse::from)
                .toList();
        return ResponseEntity.ok(incidents);
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentResponse> getIncidentById(@PathVariable Long id) {
        Incident incident = service.getIncidentById(id);
        return ResponseEntity.ok(IncidentResponse.from(incident));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<IncidentResponse> transitionStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateIncidentStatusRequest request) {
        Incident updated = service.transitionStatus(id, request);
        return ResponseEntity.ok(IncidentResponse.from(updated));
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<IncidentNoteResponse> addNote(
            @PathVariable Long id,
            @Valid @RequestBody CreateIncidentNoteRequest request) {
        IncidentNote createdNote = service.addNote(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(IncidentNoteResponse.from(createdNote));
    }

    @GetMapping("/{id}/notes")
    public ResponseEntity<List<IncidentNoteResponse>> getNotes(@PathVariable Long id) {
        List<IncidentNoteResponse> notes = service.getNotesByIncidentId(id)
                .stream()
                .map(IncidentNoteResponse::from)
                .toList();
        return ResponseEntity.ok(notes);
    }

    @PostMapping("/{id}/ai-diagnostics")
    public ResponseEntity<IncidentAiAnalysisResponse> runAiDiagnostics(
            @PathVariable Long id,
            @RequestBody(required = false) IncidentAiDiagnosticsRequest request) {
        if (aiService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        boolean forceReanalyze = request != null && request.isForceReanalyze();
        IncidentAiAnalysis analysis = aiService.requestOrRunDiagnostics(id, forceReanalyze);
        return ResponseEntity.ok(IncidentAiAnalysisResponse.from(analysis));
    }

    @GetMapping("/{id}/ai-diagnostics")
    public ResponseEntity<IncidentAiDiagnosticsResponse> getAiDiagnostics(@PathVariable Long id) {
        if (aiService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        IncidentAiDiagnosticsResponse response = aiService.getDiagnosticsSummary(id);
        return ResponseEntity.ok(response);
    }
}
