package com.spectra.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.spectra.dto.ConfirmIngestionRequest;
import com.spectra.dto.ConfirmIngestionResponse;
import com.spectra.dto.CreateMetricRequest;
import com.spectra.dto.IngestionBatchResponse;
import com.spectra.dto.IngestionPreviewResponse;
import com.spectra.entity.ServiceMetric;
import com.spectra.entity.User;
import com.spectra.exception.BadRequestException;
import com.spectra.repository.UserRepository;
import com.spectra.service.IngestionService;
import com.spectra.service.ServiceMetricService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ingestion")
@Validated
public class IngestionController {

    private final IngestionService ingestionService;
    private final ServiceMetricService metricService;
    private final UserRepository userRepository;

    public IngestionController(
            IngestionService ingestionService,
            ServiceMetricService metricService,
            UserRepository userRepository) {
        this.ingestionService = ingestionService;
        this.metricService = metricService;
        this.userRepository = userRepository;
    }

    private User resolveUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestionPreviewResponse> previewUpload(
            @RequestParam("file") MultipartFile file,
            Authentication auth) throws Exception {

        if (file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty");
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        User user = resolveUser(auth);
        IngestionPreviewResponse preview = ingestionService.previewDataset(file.getOriginalFilename(), content, user);
        return ResponseEntity.ok(preview);
    }

    @PostMapping(value = "/preview-text", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IngestionPreviewResponse> previewText(
            @RequestBody Map<String, String> payload,
            Authentication auth) {

        String content = payload.get("content");
        String filename = payload.getOrDefault("filename", "raw_telemetry.csv");
        User user = resolveUser(auth);
        IngestionPreviewResponse preview = ingestionService.previewDataset(filename, content, user);
        return ResponseEntity.ok(preview);
    }

    @PostMapping("/confirm")
    public ResponseEntity<ConfirmIngestionResponse> confirmIngestion(
            @RequestBody @Valid ConfirmIngestionRequest request,
            Authentication auth) {

        User user = resolveUser(auth);
        ConfirmIngestionResponse response = ingestionService.confirmIngestion(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/batches")
    public ResponseEntity<List<IngestionBatchResponse>> getBatches(Authentication auth) {
        User user = resolveUser(auth);
        List<IngestionBatchResponse> batches = ingestionService.getBatches(user);
        return ResponseEntity.ok(batches);
    }

    @DeleteMapping("/batches/{id}")
    public ResponseEntity<Map<String, Object>> deleteBatch(
            @PathVariable Long id,
            Authentication auth) {

        User user = resolveUser(auth);
        ingestionService.deleteBatch(id, user);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Dataset batch #" + id + " and associated telemetry records deleted successfully."
        ));
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplateCsv() {
        String template = ingestionService.getTemplateCsv();
        byte[] bytes = template.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"spectra_telemetry_template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    @PostMapping("/manual")
    public ResponseEntity<ConfirmIngestionResponse> submitManualTelemetry(
            @RequestBody @Valid com.spectra.dto.ManualTelemetryRequest request,
            Authentication auth) {

        User user = resolveUser(auth);

        IngestionPreviewResponse.TelemetryObservationDto obs = new IngestionPreviewResponse.TelemetryObservationDto();
        obs.setServiceId(request.getServiceId());
        obs.setServiceName(request.getServiceName());
        obs.setMetricName(request.getMetricName());
        obs.setMetricValue(request.getMetricValue());
        obs.setUnit(request.getUnit());
        obs.setTimestamp(request.getTimestamp() != null ? request.getTimestamp() : java.time.Instant.now().toString());
        obs.setEnvironment(request.getEnvironment());
        obs.setScenarioLabel(request.getScenarioLabel());
        obs.setGroundTruthLabel(request.getGroundTruthLabel());

        ConfirmIngestionRequest confirmReq = new ConfirmIngestionRequest();
        confirmReq.setFilename("manual_entry_" + System.currentTimeMillis());
        confirmReq.setIngestionSource("MANUAL_ENTRY");
        confirmReq.setRecords(List.of(obs));

        ConfirmIngestionResponse res = ingestionService.confirmIngestion(confirmReq, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }
}
