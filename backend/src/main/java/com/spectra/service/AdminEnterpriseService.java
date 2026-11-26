package com.spectra.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.dto.AgentSummaryResponse;
import com.spectra.dto.AlertEventResponse;
import com.spectra.dto.AnomalyEventResponse;
import com.spectra.dto.CreateEnterpriseRequest;
import com.spectra.dto.EnrollmentTokenResponse;
import com.spectra.dto.EnterpriseDetailDashboardResponse;
import com.spectra.dto.EnterpriseSummaryResponse;
import com.spectra.dto.IncidentResponse;
import com.spectra.dto.MetricResponse;
import com.spectra.dto.PlatformOverviewResponse;
import com.spectra.dto.ServiceResponse;
import com.spectra.entity.AgentCredential;
import com.spectra.entity.AgentEnrollment;
import com.spectra.entity.Enterprise;
import com.spectra.entity.EnterpriseAgent;
import com.spectra.entity.User;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.AgentCredentialRepository;
import com.spectra.repository.AgentEnrollmentRepository;
import com.spectra.repository.AlertEventRepository;
import com.spectra.repository.AnomalyEventRepository;
import com.spectra.repository.EnterpriseAgentRepository;
import com.spectra.repository.EnterpriseRepository;
import com.spectra.repository.IncidentRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.ServiceMetricRepository;
import com.spectra.repository.UserRepository;
import com.spectra.security.AgentAuthenticationFilter;

/**
 * Platform Administrator service for managing multi-enterprise onboarding,
 * enrollment tokens, agent credential lifecycle, and read-only cross-enterprise views.
 */
@Service
public class AdminEnterpriseService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Set<String> ALLOWED_ENTERPRISE_STATUSES = Set.of(
            Enterprise.STATUS_PENDING,
            Enterprise.STATUS_CONNECTED,
            Enterprise.STATUS_DISCONNECTED,
            Enterprise.STATUS_AUTHENTICATION_FAILED,
            Enterprise.STATUS_SYNC_DELAYED,
            Enterprise.STATUS_INTEGRATION_ERROR
    );

    private final EnterpriseRepository enterpriseRepository;
    private final EnterpriseAgentRepository agentRepository;
    private final AgentEnrollmentRepository enrollmentRepository;
    private final AgentCredentialRepository credentialRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final ServiceMetricRepository metricRepository;
    private final AlertEventRepository alertEventRepository;
    private final AnomalyEventRepository anomalyEventRepository;
    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public AdminEnterpriseService(
            EnterpriseRepository enterpriseRepository,
            EnterpriseAgentRepository agentRepository,
            AgentEnrollmentRepository enrollmentRepository,
            AgentCredentialRepository credentialRepository,
            MonitoredServiceRepository serviceRepository,
            ServiceMetricRepository metricRepository,
            AlertEventRepository alertEventRepository,
            AnomalyEventRepository anomalyEventRepository,
            IncidentRepository incidentRepository,
            UserRepository userRepository,
            AuditLogService auditLogService) {
        this.enterpriseRepository = enterpriseRepository;
        this.agentRepository = agentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.credentialRepository = credentialRepository;
        this.serviceRepository = serviceRepository;
        this.metricRepository = metricRepository;
        this.alertEventRepository = alertEventRepository;
        this.anomalyEventRepository = anomalyEventRepository;
        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Map<String, Object> createEnterpriseAndIssueToken(CreateEnterpriseRequest request, User adminUser) {
        String name = request.getName().trim();
        String orgCode = request.getOrganizationCode().trim().toUpperCase(Locale.ROOT);
        String slug = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            slug = orgCode.toLowerCase(Locale.ROOT);
        }

        if (enterpriseRepository.existsByOrganizationCodeIgnoreCase(orgCode)) {
            throw new IllegalArgumentException("Enterprise organization code '" + orgCode + "' already exists");
        }
        if (enterpriseRepository.existsBySlugIgnoreCase(slug)) {
            slug = slug + "-" + System.currentTimeMillis() % 10000;
        }

        Enterprise enterprise = new Enterprise();
        enterprise.setName(name);
        enterprise.setSlug(slug);
        enterprise.setOrganizationCode(orgCode);
        enterprise.setEnvironment(request.getEnvironment().trim().toUpperCase(Locale.ROOT));
        enterprise.setConnectionStatus(Enterprise.STATUS_PENDING);
        enterprise.setStatusReason("Awaiting monitoring agent enrollment and initial telemetry verification");
        enterprise.setContactName(request.getContactName() != null ? request.getContactName().trim() : null);
        enterprise.setContactEmail(request.getContactEmail() != null ? request.getContactEmail().trim() : null);
        enterprise.setApprovedBy(adminUser);
        enterprise.setApprovedAt(Instant.now());

        Enterprise saved = enterpriseRepository.save(enterprise);
        int ttlMinutes = request.getTokenTtlMinutes() != null && request.getTokenTtlMinutes() >= 5
                ? Math.min(request.getTokenTtlMinutes(), 1440)
                : 60;

        EnrollmentTokenResponse enrollment = issueEnrollmentTokenInternal(saved, adminUser, ttlMinutes);

        auditLogService.recordAction(
                "ENTERPRISE_CREATED",
                adminUser != null ? adminUser.getEmail() : "ADMIN",
                "Created enterprise '" + saved.getName() + "' (" + saved.getOrganizationCode() + ") with status PENDING"
        );

        return Map.of(
                "enterprise", toSummary(saved),
                "enrollment", enrollment
        );
    }

    @Transactional
    public EnrollmentTokenResponse issueEnrollmentToken(Long enterpriseId, User adminUser, Integer ttlMinutes) {
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new ResourceNotFoundException("Enterprise not found with id: " + enterpriseId));

        int ttl = ttlMinutes != null && ttlMinutes >= 5 ? Math.min(ttlMinutes, 1440) : 60;
        EnrollmentTokenResponse response = issueEnrollmentTokenInternal(enterprise, adminUser, ttl);

        auditLogService.recordAction(
                "AGENT_ENROLLMENT_TOKEN_ISSUED",
                adminUser != null ? adminUser.getEmail() : "ADMIN",
                "Issued single-use enrollment token for enterprise '" + enterprise.getName() + "' (prefix=" + response.tokenPrefix() + ")"
        );
        return response;
    }

    private EnrollmentTokenResponse issueEnrollmentTokenInternal(Enterprise enterprise, User adminUser, int ttlMinutes) {
        // Expire/revoke any previous unused active tokens for clean single-active-token hygiene
        List<AgentEnrollment> existingActive = enrollmentRepository.findByEnterpriseIdAndStatus(
                enterprise.getId(), AgentEnrollment.STATUS_ACTIVE);
        for (AgentEnrollment old : existingActive) {
            old.setStatus(AgentEnrollment.STATUS_REVOKED);
            enrollmentRepository.save(old);
        }

        String rawToken = "sp_enr_" + generateSecretToken(24);
        String tokenHash = AgentAuthenticationFilter.sha256Hex(rawToken);
        String prefix = rawToken.substring(0, Math.min(14, rawToken.length()));

        AgentEnrollment enrollment = new AgentEnrollment();
        enrollment.setEnterprise(enterprise);
        enrollment.setTokenHash(tokenHash);
        enrollment.setTokenPrefix(prefix);
        enrollment.setStatus(AgentEnrollment.STATUS_ACTIVE);
        enrollment.setExpiresAt(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES));
        enrollment.setCreatedBy(adminUser);

        AgentEnrollment saved = enrollmentRepository.save(enrollment);

        String installCommand = "java -jar spectra-agent.jar --server-url=http://localhost:8080 --enroll-token=" + rawToken;

        return new EnrollmentTokenResponse(
                saved.getId(),
                enterprise.getId(),
                enterprise.getName(),
                enterprise.getOrganizationCode(),
                saved.getTokenPrefix(),
                rawToken,
                saved.getStatus(),
                saved.getExpiresAt(),
                installCommand
        );
    }

    @Transactional(readOnly = true)
    public PlatformOverviewResponse getPlatformOverview() {
        List<Enterprise> all = enterpriseRepository.findAllByOrderByCreatedAtDesc();
        List<EnterpriseSummaryResponse> summaries = all.stream().map(this::toSummary).toList();

        long connected = all.stream().filter(e -> Enterprise.STATUS_CONNECTED.equals(e.getConnectionStatus())).count();
        long pending = all.stream().filter(e -> Enterprise.STATUS_PENDING.equals(e.getConnectionStatus())).count();
        long degraded = all.stream().filter(e ->
                Enterprise.STATUS_SYNC_DELAYED.equals(e.getConnectionStatus())
                        || Enterprise.STATUS_INTEGRATION_ERROR.equals(e.getConnectionStatus())
                        || Enterprise.STATUS_AUTHENTICATION_FAILED.equals(e.getConnectionStatus())).count();
        long disconnected = all.stream().filter(e -> Enterprise.STATUS_DISCONNECTED.equals(e.getConnectionStatus())).count();

        long totalServices = serviceRepository.count();
        long activeAlerts = alertEventRepository.countByStatusIn(List.of("TRIGGERED", "ACKNOWLEDGED"));
        long openIncidents = incidentRepository.countByStatusIn(List.of("OPEN", "INVESTIGATING"));

        List<EnterpriseAgent> agents = agentRepository.findAll();
        long totalAgents = agents.size();
        long onlineAgents = agents.stream().filter(a -> a.isActive() && EnterpriseAgent.STATUS_ONLINE.equals(a.getStatus())).count();
        long delayedAgents = agents.stream().filter(a -> a.isActive() && EnterpriseAgent.STATUS_SYNC_DELAYED.equals(a.getStatus())).count();
        long disconnectedAgents = totalAgents - onlineAgents - delayedAgents;

        return new PlatformOverviewResponse(
                all.size(),
                connected,
                pending,
                degraded,
                disconnected,
                totalServices,
                activeAlerts,
                openIncidents,
                totalAgents,
                onlineAgents,
                delayedAgents,
                Math.max(0, disconnectedAgents),
                summaries
        );
    }

    @Transactional(readOnly = true)
    public List<EnterpriseSummaryResponse> getAllEnterprises() {
        return enterpriseRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public EnterpriseDetailDashboardResponse getEnterpriseDetailDashboard(Long enterpriseId) {
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new ResourceNotFoundException("Enterprise not found with id: " + enterpriseId));

        List<ServiceResponse> services = serviceRepository.findByEnterpriseIdWithDetailsOrderByCreatedAtDesc(enterpriseId)
                .stream().map(ServiceResponse::from).toList();
        List<MetricResponse> metrics = metricRepository.findByServiceEnterpriseIdOrderByRecordedAtDescIdDesc(
                enterpriseId, PageRequest.of(0, 50)).stream().map(MetricResponse::from).toList();
        List<AlertEventResponse> alerts = alertEventRepository.findByServiceEnterpriseIdOrderByTriggeredAtDescIdDesc(enterpriseId)
                .stream().map(AlertEventResponse::from).toList();
        List<AnomalyEventResponse> anomalies = anomalyEventRepository.findByServiceEnterpriseIdOrderByDetectedAtDescIdDesc(
                enterpriseId, PageRequest.of(0, 50)).stream().map(AnomalyEventResponse::from).toList();
        List<IncidentResponse> incidents = incidentRepository.findByServiceEnterpriseIdOrderByTriggeredAtDescIdDesc(enterpriseId)
                .stream().map(IncidentResponse::from).toList();
        List<AgentSummaryResponse> agents = agentRepository.findByEnterpriseIdOrderByCreatedAtDesc(enterpriseId)
                .stream().map(AgentSummaryResponse::from).toList();

        return new EnterpriseDetailDashboardResponse(
                toSummary(enterprise),
                services,
                metrics,
                alerts,
                anomalies,
                incidents,
                agents,
                true
        );
    }

    @Transactional
    public EnterpriseSummaryResponse updateEnterpriseStatus(Long enterpriseId, String rawStatus, String reason, User adminUser) {
        if (rawStatus == null || rawStatus.isBlank()) {
            throw new BadRequestException("Connection status is required");
        }
        String status = rawStatus.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_ENTERPRISE_STATUSES.contains(status)) {
            throw new BadRequestException("Invalid enterprise status: " + rawStatus);
        }

        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new ResourceNotFoundException("Enterprise not found with id: " + enterpriseId));
        enterprise.setConnectionStatus(status);
        enterprise.setStatusReason(reason != null ? reason.trim() : "Status updated by platform administrator");
        Enterprise saved = enterpriseRepository.save(enterprise);

        if (Enterprise.STATUS_DISCONNECTED.equals(status)) {
            List<EnterpriseAgent> agents = agentRepository.findByEnterpriseIdOrderByCreatedAtDesc(enterpriseId);
            for (EnterpriseAgent agent : agents) {
                agent.setStatus(EnterpriseAgent.STATUS_DISCONNECTED);
                agentRepository.save(agent);
            }
        }

        auditLogService.recordAction(
                "ENTERPRISE_STATUS_UPDATED",
                adminUser != null ? adminUser.getEmail() : "ADMIN",
                "Updated enterprise '" + saved.getName() + "' status to " + status
        );

        return toSummary(saved);
    }

    @Transactional(readOnly = true)
    public List<AgentSummaryResponse> getAllAgents() {
        return agentRepository.findAllWithEnterpriseOrderByCreatedAtDesc().stream()
                .map(AgentSummaryResponse::from)
                .toList();
    }

    @Transactional
    public Map<String, Object> rotateAgentCredential(Long agentId, User adminUser) {
        EnterpriseAgent agent = agentRepository.findByIdWithEnterprise(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitoring agent not found with id: " + agentId));

        Instant graceExpiry = Instant.now().plus(15, ChronoUnit.MINUTES);
        List<AgentCredential> activeCreds = credentialRepository.findByAgentIdAndStatusIn(
                agentId, List.of(AgentCredential.STATUS_ACTIVE, AgentCredential.STATUS_ROTATING));
        for (AgentCredential existing : activeCreds) {
            existing.setStatus(AgentCredential.STATUS_ROTATING);
            if (existing.getExpiresAt() == null || existing.getExpiresAt().isAfter(graceExpiry)) {
                existing.setExpiresAt(graceExpiry);
            }
            credentialRepository.save(existing);
        }

        String keyId = "sp_key_" + generateSecretToken(10);
        String secret = "sp_sec_" + generateSecretToken(24);

        AgentCredential newCred = new AgentCredential();
        newCred.setAgent(agent);
        newCred.setKeyId(keyId);
        newCred.setSecretHash(AgentAuthenticationFilter.sha256Hex(secret));
        newCred.setStatus(AgentCredential.STATUS_ACTIVE);
        credentialRepository.save(newCred);

        auditLogService.recordAction(
                "AGENT_CREDENTIAL_ROTATED",
                adminUser != null ? adminUser.getEmail() : "ADMIN",
                "Rotated credential for agent '" + agent.getAgentCode() + "' (new keyId=" + keyId + ")"
        );

        return Map.of(
                "agentId", agent.getId(),
                "agentCode", agent.getAgentCode(),
                "keyId", keyId,
                "agentCredential", keyId + ":" + secret,
                "previousCredentialGraceEndsAt", graceExpiry.toString()
        );
    }

    @Transactional
    public AgentSummaryResponse deactivateAgent(Long agentId, User adminUser) {
        EnterpriseAgent agent = agentRepository.findByIdWithEnterprise(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitoring agent not found with id: " + agentId));

        agent.setActive(false);
        agent.setStatus(EnterpriseAgent.STATUS_DISCONNECTED);
        agent.setLastSyncError("Deactivated by platform administrator");
        EnterpriseAgent saved = agentRepository.save(agent);

        List<AgentCredential> creds = credentialRepository.findByAgentIdAndStatusIn(
                agentId, List.of(AgentCredential.STATUS_ACTIVE, AgentCredential.STATUS_ROTATING));
        for (AgentCredential cred : creds) {
            cred.setStatus(AgentCredential.STATUS_REVOKED);
            cred.setRevokedAt(Instant.now());
            credentialRepository.save(cred);
        }

        auditLogService.recordAction(
                "AGENT_DEACTIVATED",
                adminUser != null ? adminUser.getEmail() : "ADMIN",
                "Deactivated agent '" + agent.getAgentCode() + "' and revoked credentials"
        );

        return AgentSummaryResponse.from(saved);
    }

    public EnterpriseSummaryResponse toSummary(Enterprise ent) {
        long svcCount = serviceRepository.countByEnterpriseId(ent.getId());
        long activeSvcCount = serviceRepository.countByEnterpriseIdAndActiveTrue(ent.getId());
        List<EnterpriseAgent> agents = agentRepository.findByEnterpriseIdOrderByCreatedAtDesc(ent.getId());
        long onlineAgents = agents.stream()
                .filter(a -> a.isActive() && EnterpriseAgent.STATUS_ONLINE.equals(a.getStatus()))
                .count();
        long activeAlerts = alertEventRepository.countByServiceEnterpriseIdAndStatusIn(
                ent.getId(), List.of("TRIGGERED", "ACKNOWLEDGED"));
        long openIncidents = incidentRepository.countByServiceEnterpriseIdAndStatusIn(
                ent.getId(), List.of("OPEN", "INVESTIGATING"));
        long userCount = userRepository.countByEnterpriseId(ent.getId());

        return new EnterpriseSummaryResponse(
                ent.getId(),
                ent.getName(),
                ent.getSlug(),
                ent.getOrganizationCode(),
                ent.getEnvironment(),
                ent.getConnectionStatus(),
                ent.getStatusReason(),
                ent.getContactName(),
                ent.getContactEmail(),
                ent.getLastTelemetryAt(),
                ent.getCreatedAt(),
                svcCount,
                activeSvcCount,
                agents.size(),
                onlineAgents,
                activeAlerts,
                openIncidents,
                userCount
        );
    }

    public static String generateSecretToken(int byteCount) {
        byte[] bytes = new byte[byteCount];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
