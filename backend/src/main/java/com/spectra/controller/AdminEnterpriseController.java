package com.spectra.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spectra.dto.AgentSummaryResponse;
import com.spectra.dto.CreateEnterpriseRequest;
import com.spectra.dto.EnrollmentTokenResponse;
import com.spectra.dto.EnterpriseDetailDashboardResponse;
import com.spectra.dto.EnterpriseSummaryResponse;
import com.spectra.dto.PlatformOverviewResponse;
import com.spectra.entity.User;
import com.spectra.repository.UserRepository;
import com.spectra.service.AdminEnterpriseService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
@Validated
public class AdminEnterpriseController {

    private final AdminEnterpriseService adminEnterpriseService;
    private final UserRepository userRepository;

    public AdminEnterpriseController(AdminEnterpriseService adminEnterpriseService, UserRepository userRepository) {
        this.adminEnterpriseService = adminEnterpriseService;
        this.userRepository = userRepository;
    }

    private User resolveUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    @GetMapping("/overview")
    public ResponseEntity<PlatformOverviewResponse> getOverview() {
        return ResponseEntity.ok(adminEnterpriseService.getPlatformOverview());
    }

    @GetMapping("/enterprises")
    public ResponseEntity<List<EnterpriseSummaryResponse>> getEnterprises() {
        return ResponseEntity.ok(adminEnterpriseService.getAllEnterprises());
    }

    @PostMapping("/enterprises")
    public ResponseEntity<Map<String, Object>> createEnterprise(
            @Valid @RequestBody CreateEnterpriseRequest request,
            Authentication auth) {
        User admin = resolveUser(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminEnterpriseService.createEnterpriseAndIssueToken(request, admin));
    }

    @GetMapping("/enterprises/{enterpriseId}")
    public ResponseEntity<EnterpriseDetailDashboardResponse> getEnterpriseDashboard(@PathVariable Long enterpriseId) {
        return ResponseEntity.ok(adminEnterpriseService.getEnterpriseDetailDashboard(enterpriseId));
    }

    @PatchMapping("/enterprises/{enterpriseId}/status")
    public ResponseEntity<EnterpriseSummaryResponse> updateEnterpriseStatus(
            @PathVariable Long enterpriseId,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        User admin = resolveUser(auth);
        String status = body != null ? body.get("status") : null;
        String reason = body != null ? body.get("reason") : null;
        return ResponseEntity.ok(adminEnterpriseService.updateEnterpriseStatus(enterpriseId, status, reason, admin));
    }

    @PostMapping("/enterprises/{enterpriseId}/enrollment-token")
    public ResponseEntity<EnrollmentTokenResponse> generateEnrollmentToken(
            @PathVariable Long enterpriseId,
            @RequestParam(required = false) Integer ttlMinutes,
            Authentication auth) {
        User admin = resolveUser(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminEnterpriseService.issueEnrollmentToken(enterpriseId, admin, ttlMinutes));
    }

    @GetMapping("/agents")
    public ResponseEntity<List<AgentSummaryResponse>> getAllAgents() {
        return ResponseEntity.ok(adminEnterpriseService.getAllAgents());
    }

    @PostMapping("/agents/{agentId}/credentials/rotate")
    public ResponseEntity<Map<String, Object>> rotateAgentCredential(
            @PathVariable Long agentId,
            Authentication auth) {
        User admin = resolveUser(auth);
        return ResponseEntity.ok(adminEnterpriseService.rotateAgentCredential(agentId, admin));
    }

    @PostMapping("/agents/{agentId}/deactivate")
    public ResponseEntity<AgentSummaryResponse> deactivateAgent(
            @PathVariable Long agentId,
            Authentication auth) {
        User admin = resolveUser(auth);
        return ResponseEntity.ok(adminEnterpriseService.deactivateAgent(agentId, admin));
    }
}
