package com.spectra.controller;

import java.util.List;

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
import org.springframework.web.bind.annotation.RestController;

import com.spectra.dto.CreateIntegrationRequest;
import com.spectra.dto.EnterpriseIntegrationResponse;
import com.spectra.dto.UpdateIntegrationStatusRequest;
import com.spectra.entity.User;
import com.spectra.repository.UserRepository;
import com.spectra.service.EnterpriseIntegrationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/integrations")
@Validated
public class EnterpriseIntegrationController {

    private final EnterpriseIntegrationService integrationService;
    private final UserRepository userRepository;

    public EnterpriseIntegrationController(
            EnterpriseIntegrationService integrationService,
            UserRepository userRepository) {
        this.integrationService = integrationService;
        this.userRepository = userRepository;
    }

    private User resolveUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    @PostMapping("/request")
    public ResponseEntity<EnterpriseIntegrationResponse> createRequest(
            @Valid @RequestBody CreateIntegrationRequest request,
            Authentication auth) {

        User user = resolveUser(auth);
        EnterpriseIntegrationResponse response = integrationService.createRequest(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<EnterpriseIntegrationResponse>> getIntegrations(Authentication auth) {
        User user = resolveUser(auth);
        List<EnterpriseIntegrationResponse> list = integrationService.getIntegrations(user);
        return ResponseEntity.ok(list);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<EnterpriseIntegrationResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateIntegrationStatusRequest request,
            Authentication auth) {

        User adminUser = resolveUser(auth);
        EnterpriseIntegrationResponse response = integrationService.updateStatus(id, request, adminUser);
        return ResponseEntity.ok(response);
    }
}
