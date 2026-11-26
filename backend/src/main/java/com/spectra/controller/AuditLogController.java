package com.spectra.controller;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spectra.entity.AuditLog;
import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.exception.BadRequestException;
import com.spectra.repository.AuditLogRepository;
import com.spectra.repository.UserRepository;

@RestController
@RequestMapping("/api/audit-logs")
@Validated
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditLogController(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    private User resolveUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    @GetMapping
    public ResponseEntity<List<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "50") int limit,
            Authentication auth) {

        User user = resolveUser(auth);
        if (user == null || user.getRole() != Role.ADMIN) {
            throw new BadRequestException("Only administrators can view audit logs");
        }

        int safeLimit = Math.max(1, Math.min(100, limit));
        List<AuditLog> list = auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit));
        return ResponseEntity.ok(list);
    }
}
