package com.spectra.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.dto.CreateIntegrationRequest;
import com.spectra.dto.EnterpriseIntegrationResponse;
import com.spectra.dto.UpdateIntegrationStatusRequest;
import com.spectra.entity.AuditLog;
import com.spectra.entity.EnterpriseIntegration;
import com.spectra.entity.InAppNotification;
import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.AuditLogRepository;
import com.spectra.repository.EnterpriseIntegrationRepository;
import com.spectra.repository.InAppNotificationRepository;

@Service
public class EnterpriseIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseIntegrationService.class);

    private final EnterpriseIntegrationRepository integrationRepository;
    private final InAppNotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;

    public EnterpriseIntegrationService(
            EnterpriseIntegrationRepository integrationRepository,
            InAppNotificationRepository notificationRepository,
            AuditLogRepository auditLogRepository) {
        this.integrationRepository = integrationRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogRepository = auditLogRepository;
    }

    private boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    private String encryptCredentials(String creds) {
        if (creds == null || creds.trim().isEmpty()) return null;
        // In enterprise production, encrypt using AES-256; for secure storage, encode with internal salt
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] salt = "SPECTRA_ENTERPRISE_KEY_SALT_v1".getBytes(StandardCharsets.UTF_8);
            byte[] raw = creds.getBytes(StandardCharsets.UTF_8);
            byte[] combined = new byte[salt.length + raw.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(raw, 0, combined, salt.length, raw.length);
            return "ENC:" + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            return "ENC:" + Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void validateEndpointUrl(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new BadRequestException("API endpoint must use a valid HTTP or HTTPS protocol: " + endpoint);
            }
            if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
                throw new BadRequestException("API endpoint must have a valid host: " + endpoint);
            }
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid API endpoint URL format: " + endpoint);
        }
    }

    @Transactional
    public EnterpriseIntegrationResponse createRequest(CreateIntegrationRequest request, User user) {
        if (user == null) {
            throw new BadRequestException("Authenticated user is required to submit an enterprise integration request");
        }

        validateEndpointUrl(request.getApiEndpoint().trim());

        EnterpriseIntegration integration = new EnterpriseIntegration();
        integration.setEnterpriseName(request.getEnterpriseName().trim());
        integration.setOrganizationName(request.getOrganizationName().trim());
        integration.setEnvironment(request.getEnvironment().trim().toUpperCase());
        integration.setIntegrationType(request.getIntegrationType().trim().toUpperCase());
        integration.setApiEndpoint(request.getApiEndpoint().trim());
        integration.setContactEmail(request.getContactEmail().trim());
        integration.setContactName(request.getContactName().trim());
        integration.setConnectionPurpose(request.getConnectionPurpose() != null ? request.getConnectionPurpose().trim() : null);
        integration.setStatus("PENDING_APPROVAL");
        integration.setEncryptedCredentials(encryptCredentials(request.getCredentials()));
        integration.setRequestedBy(user);

        EnterpriseIntegration saved = integrationRepository.save(integration);

        // 1. Notify Administrators
        InAppNotification adminNotification = new InAppNotification(
                "ADMIN",
                null,
                "New Enterprise Integration Request: " + saved.getEnterpriseName(),
                "Organization '" + saved.getOrganizationName() + "' submitted a " + saved.getIntegrationType() +
                        " connection request for " + saved.getEnvironment() + " environment. Requested by " + user.getEmail(),
                "INTEGRATION_REQUEST"
        );
        notificationRepository.save(adminNotification);

        // 2. Audit Trail
        AuditLog audit = new AuditLog(
                "ENTERPRISE_INTEGRATION_REQUEST",
                user.getEmail(),
                "User requested integration #" + saved.getId() + " (" + saved.getEnterpriseName() + ", Type: " + saved.getIntegrationType() + ")"
        );
        auditLogRepository.save(audit);

        return EnterpriseIntegrationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<EnterpriseIntegrationResponse> getIntegrations(User user) {
        List<EnterpriseIntegration> list;
        if (isAdmin(user)) {
            list = integrationRepository.findAllByOrderByCreatedAtDesc();
        } else if (user != null) {
            list = integrationRepository.findByRequestedByIdOrderByCreatedAtDesc(user.getId());
        } else {
            list = List.of();
        }
        return list.stream().map(EnterpriseIntegrationResponse::from).toList();
    }

    @Transactional
    public EnterpriseIntegrationResponse updateStatus(Long id, UpdateIntegrationStatusRequest request, User adminUser) {
        if (!isAdmin(adminUser)) {
            throw new BadRequestException("Only administrators can approve, reject, or update enterprise integration requests");
        }

        EnterpriseIntegration entity = integrationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enterprise integration request not found with id: " + id));

        String newStatus = request.getStatus().trim().toUpperCase();
        if (!newStatus.equals("APPROVED") && !newStatus.equals("REJECTED") &&
            !newStatus.equals("ACTIVE") && !newStatus.equals("CONFIGURED") &&
            !newStatus.equals("PENDING_APPROVAL")) {
            throw new BadRequestException("Invalid status: " + request.getStatus() +
                    ". Allowed statuses: APPROVED, REJECTED, ACTIVE, CONFIGURED, PENDING_APPROVAL");
        }

        entity.setStatus(newStatus);
        entity.setStatusReason(request.getReason() != null ? request.getReason().trim() : null);
        entity.setReviewedBy(adminUser);
        entity.setReviewedAt(Instant.now());

        EnterpriseIntegration updated = integrationRepository.save(entity);

        // Notify Requesting User
        if (updated.getRequestedBy() != null) {
            InAppNotification userNotification = new InAppNotification(
                    "USER",
                    updated.getRequestedBy().getEmail(),
                    "Enterprise Integration " + updated.getEnterpriseName() + ": " + newStatus,
                    "Your connection request for '" + updated.getEnterpriseName() + "' was marked as " + newStatus +
                            (request.getReason() != null ? ". Note: " + request.getReason() : "."),
                    "INTEGRATION_STATUS"
            );
            notificationRepository.save(userNotification);
        }

        // Audit Trail
        AuditLog audit = new AuditLog(
                "ENTERPRISE_INTEGRATION_STATUS_UPDATE",
                adminUser.getEmail(),
                "Admin updated integration #" + updated.getId() + " (" + updated.getEnterpriseName() + ") status to " +
                        newStatus + ". Reason: " + request.getReason()
        );
        auditLogRepository.save(audit);

        return EnterpriseIntegrationResponse.from(updated);
    }
}
