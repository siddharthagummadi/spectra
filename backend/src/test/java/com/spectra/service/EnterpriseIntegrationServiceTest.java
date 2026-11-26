package com.spectra.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.spectra.dto.CreateIntegrationRequest;
import com.spectra.dto.EnterpriseIntegrationResponse;
import com.spectra.dto.UpdateIntegrationStatusRequest;
import com.spectra.entity.AuditLog;
import com.spectra.entity.EnterpriseIntegration;
import com.spectra.entity.InAppNotification;
import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.exception.BadRequestException;
import com.spectra.repository.AuditLogRepository;
import com.spectra.repository.EnterpriseIntegrationRepository;
import com.spectra.repository.InAppNotificationRepository;

@ExtendWith(MockitoExtension.class)
class EnterpriseIntegrationServiceTest {

    @Mock
    private EnterpriseIntegrationRepository integrationRepository;

    @Mock
    private InAppNotificationRepository notificationRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private EnterpriseIntegrationService integrationService;

    private User adminUser;
    private User normalUser;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@spectra.local");
        adminUser.setRole(Role.ADMIN);

        normalUser = new User();
        normalUser.setId(2L);
        normalUser.setEmail("developer@enterprise.com");
        normalUser.setRole(Role.USER);
    }

    @Test
    @DisplayName("Should successfully submit enterprise integration request and notify admin")
    void testCreateRequest_Success() {
        CreateIntegrationRequest request = new CreateIntegrationRequest();
        request.setEnterpriseName("Acme Payments Cloud");
        request.setOrganizationName("Acme Global");
        request.setEnvironment("PRODUCTION");
        request.setIntegrationType("DATADOG");
        request.setApiEndpoint("https://api.datadoghq.com/v1");
        request.setContactName("Jane Doe");
        request.setContactEmail("jane@acme.com");
        request.setConnectionPurpose("Ingest production gateway latency metrics");
        request.setCredentials("api_key_secret_12345");

        when(integrationRepository.save(any(EnterpriseIntegration.class))).thenAnswer(inv -> {
            EnterpriseIntegration ent = inv.getArgument(0);
            ent.setId(50L);
            ent.setCreatedAt(Instant.now());
            return ent;
        });

        EnterpriseIntegrationResponse response = integrationService.createRequest(request, normalUser);

        assertNotNull(response);
        assertEquals(50L, response.getId());
        assertEquals("Acme Payments Cloud", response.getEnterpriseName());
        assertEquals("PENDING_APPROVAL", response.getStatus());

        // Verify Admin notification was saved
        verify(notificationRepository, times(1)).save(any(InAppNotification.class));
        // Verify audit log was recorded
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should reject invalid API endpoint format")
    void testCreateRequest_InvalidEndpoint() {
        CreateIntegrationRequest request = new CreateIntegrationRequest();
        request.setEnterpriseName("Bad URL Corp");
        request.setOrganizationName("Bad URL");
        request.setEnvironment("TEST");
        request.setIntegrationType("KAFKA");
        request.setApiEndpoint("ftp://not-an-http-url");
        request.setContactName("Jane Doe");
        request.setContactEmail("jane@bad.com");

        assertThrows(BadRequestException.class, () -> integrationService.createRequest(request, normalUser));
    }

    @Test
    @DisplayName("Admin can approve integration and trigger user notification")
    void testUpdateStatus_AdminApproval() {
        EnterpriseIntegration integration = new EnterpriseIntegration();
        integration.setId(60L);
        integration.setEnterpriseName("Global Cloud");
        integration.setStatus("PENDING_APPROVAL");
        integration.setRequestedBy(normalUser);

        when(integrationRepository.findById(60L)).thenReturn(Optional.of(integration));
        when(integrationRepository.save(any(EnterpriseIntegration.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateIntegrationStatusRequest req = new UpdateIntegrationStatusRequest();
        req.setStatus("APPROVED");
        req.setReason("Security review passed and API token confirmed");

        EnterpriseIntegrationResponse res = integrationService.updateStatus(60L, req, adminUser);

        assertNotNull(res);
        assertEquals("APPROVED", res.getStatus());
        assertEquals("Security review passed and API token confirmed", res.getStatusReason());

        // Verify notification for requesting user
        verify(notificationRepository, times(1)).save(any(InAppNotification.class));
        // Verify audit log
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Non-admin user cannot approve or reject integration requests")
    void testUpdateStatus_ForbiddenForNonAdmin() {
        UpdateIntegrationStatusRequest req = new UpdateIntegrationStatusRequest();
        req.setStatus("APPROVED");

        assertThrows(BadRequestException.class, () -> integrationService.updateStatus(60L, req, normalUser));
    }

    @Test
    @DisplayName("Admin retrieves all integrations while user retrieves only their own")
    void testGetIntegrations_RoleScoping() {
        EnterpriseIntegration i1 = new EnterpriseIntegration();
        i1.setId(1L);
        i1.setRequestedBy(normalUser);

        when(integrationRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(i1));
        when(integrationRepository.findByRequestedByIdOrderByCreatedAtDesc(normalUser.getId())).thenReturn(List.of(i1));

        List<EnterpriseIntegrationResponse> adminList = integrationService.getIntegrations(adminUser);
        assertEquals(1, adminList.size());
        verify(integrationRepository, times(1)).findAllByOrderByCreatedAtDesc();

        List<EnterpriseIntegrationResponse> userList = integrationService.getIntegrations(normalUser);
        assertEquals(1, userList.size());
        verify(integrationRepository, times(1)).findByRequestedByIdOrderByCreatedAtDesc(normalUser.getId());
    }
}
