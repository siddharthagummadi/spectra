package com.spectra.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spectra.dto.EvaluationExperimentResponse;
import com.spectra.dto.RunEvaluationRequest;
import com.spectra.entity.User;
import com.spectra.repository.UserRepository;
import com.spectra.service.EvaluationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/evaluation")
@Validated
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final UserRepository userRepository;

    public EvaluationController(
            EvaluationService evaluationService,
            UserRepository userRepository) {
        this.evaluationService = evaluationService;
        this.userRepository = userRepository;
    }

    private User resolveUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    @PostMapping("/run")
    public ResponseEntity<List<EvaluationExperimentResponse>> runEvaluation(
            @Valid @RequestBody RunEvaluationRequest request,
            Authentication auth) {

        User user = resolveUser(auth);
        List<EvaluationExperimentResponse> results = evaluationService.runEvaluation(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(results);
    }

    @GetMapping("/experiments")
    public ResponseEntity<List<EvaluationExperimentResponse>> getExperiments() {
        List<EvaluationExperimentResponse> list = evaluationService.getExperiments();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/experiments/{id}/csv")
    public ResponseEntity<byte[]> exportCsv(@PathVariable Long id) {
        String csv = evaluationService.exportCsv(id);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"spectra_evaluation_" + id + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }
}
