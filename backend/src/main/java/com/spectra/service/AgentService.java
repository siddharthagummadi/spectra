package com.spectra.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.dto.AgentConfigResponse;
import com.spectra.dto.AgentEnrollRequest;
import com.spectra.dto.AgentEnrollResponse;
import com.spectra.dto.AgentHeartbeatRequest;
import com.spectra.dto.AgentSummaryResponse;
import com.spectra.dto.AgentTelemetryBatchRequest;
import com.spectra.dto.AgentTelemetryBatchResponse;
import com.spectra.entity.AgentCredential;
import com.spectra.entity.AgentEnrollment;
import com.spectra.entity.AgentHeartbeat;
import com.spectra.entity.AgentTelemetryBatch;
import com.spectra.entity.Enterprise;
import com.spectra.entity.EnterpriseAgent;
import com.spectra.entity.MonitoredService;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.AgentCredentialRepository;
import com.spectra.repository.AgentEnrollmentRepository;
import com.spectra.repository.AgentHeartbeatRepository;
import com.spectra.repository.AgentTelemetryBatchRepository;
import com.spectra.repository.EnterpriseAgentRepository;
import com.spectra.repository.EnterpriseRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.security.AgentAuthenticationFilter;
import com.spectra.security.AgentPrincipal;

/**
 * Handles standalone Enterprise Monitoring Agent enrollment, heartbeats,
 * configuration retrieval, status reporting, and deduplicated telemetry ingestion.
 */
@Service
public class AgentService {

    private static final List<String> SUPPORTED_METRICS = List.of(
            "CPU_USAGE", "MEMORY_USAGE", "DISK_USAGE", "NETWORK_USAGE", "SYSTEM_UPTIME",
            "RESPONSE_TIME", "ERROR_RATE", "REQUEST_COUNT", "SERVICE_AVAILABILITY", "APPLICATION_HEALTH"
    );

    private final AgentEnrollmentRepository enrollmentRepository;
    private final AgentCredentialRepository credentialRepository;
    private final EnterpriseAgentRepository agentRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final AgentHeartbeatRepository heartbeatRepository;
    private final AgentTelemetryBatchRepository batchRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final ServiceMetricService metricService;
    private final AlertService alertService;
    private final AuditLogService auditLogService;

    public AgentService(
            AgentEnrollmentRepository enrollmentRepository,
            AgentCredentialRepository credentialRepository,
            EnterpriseAgentRepository agentRepository,
            EnterpriseRepository enterpriseRepository,
            AgentHeartbeatRepository heartbeatRepository,
            AgentTelemetryBatchRepository batchRepository,
            MonitoredServiceRepository serviceRepository,
            ServiceMetricService metricService,
            AlertService alertService,
            AuditLogService auditLogService) {
        this.enrollmentRepository = enrollmentRepository;
        this.credentialRepository = credentialRepository;
        this.agentRepository = agentRepository;
        this.enterpriseRepository = enterpriseRepository;
        this.heartbeatRepository = heartbeatRepository;
        this.batchRepository = batchRepository;
        this.serviceRepository = serviceRepository;
        this.metricService = metricService;
        this.alertService = alertService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AgentEnrollResponse enroll(AgentEnrollRequest request) {
        String tokenHash = AgentAuthenticationFilter.sha256Hex(request.getEnrollmentToken().trim());
        AgentEnrollment enrollment = enrollmentRepository.findByTokenHashWithEnterprise(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid enrollment token"));

        if (!AgentEnrollment.STATUS_ACTIVE.equals(enrollment.getStatus())) {
            throw new BadCredentialsException("Enrollment token is no longer active (" + enrollment.getStatus() + ")");
        }
        if (enrollment.getExpiresAt().isBefore(Instant.now())) {
            enrollment.setStatus(AgentEnrollment.STATUS_EXPIRED);
            enrollmentRepository.save(enrollment);
            throw new BadCredentialsException("Enrollment token has expired");
        }

        Enterprise enterprise = enrollment.getEnterprise();
        if (Enterprise.STATUS_DISCONNECTED.equals(enterprise.getConnectionStatus())) {
            throw new BadRequestException("Enterprise connection is disconnected and cannot enroll agents");
        }

        String agentCode = request.getAgentCode().trim();
        EnterpriseAgent agent = agentRepository.findByEnterpriseIdAndAgentCode(enterprise.getId(), agentCode)
                .orElseGet(EnterpriseAgent::new);
        agent.setEnterprise(enterprise);
        agent.setAgentCode(agentCode);
        agent.setHostname(request.getHostname().trim());
        agent.setOsInfo(request.getOsInfo() != null ? request.getOsInfo().trim() : null);
        agent.setAgentVersion(request.getAgentVersion() != null && !request.getAgentVersion().isBlank()
                ? request.getAgentVersion().trim()
                : "1.0.0");
        agent.setStatus(EnterpriseAgent.STATUS_ONLINE);
        agent.setActive(true);
        agent.setLastHeartbeatAt(Instant.now());
        agent.setLastSyncError(null);
        EnterpriseAgent savedAgent = agentRepository.save(agent);

        // Revoke any prior active credentials for this agent code on re-enrollment
        List<AgentCredential> oldCreds = credentialRepository.findByAgentIdAndStatusIn(
                savedAgent.getId(), List.of(AgentCredential.STATUS_ACTIVE, AgentCredential.STATUS_ROTATING));
        for (AgentCredential old : oldCreds) {
            old.setStatus(AgentCredential.STATUS_REVOKED);
            old.setRevokedAt(Instant.now());
            credentialRepository.save(old);
        }

        String keyId = "sp_key_" + AdminEnterpriseService.generateSecretToken(10);
        String secret = "sp_sec_" + AdminEnterpriseService.generateSecretToken(24);
        AgentCredential credential = new AgentCredential();
        credential.setAgent(savedAgent);
        credential.setKeyId(keyId);
        credential.setSecretHash(AgentAuthenticationFilter.sha256Hex(secret));
        credential.setStatus(AgentCredential.STATUS_ACTIVE);
        credentialRepository.save(credential);

        enrollment.setStatus(AgentEnrollment.STATUS_CONSUMED);
        enrollment.setConsumedAt(Instant.now());
        enrollment.setConsumedByAgent(savedAgent);
        enrollmentRepository.save(enrollment);

        enterprise.setStatusReason("Agent '" + savedAgent.getAgentCode() + "' enrolled; awaiting initial telemetry verification");
        enterpriseRepository.save(enterprise);

        auditLogService.recordAction(
                "AGENT_ENROLLED",
                savedAgent.getAgentCode(),
                "Enrolled agent '" + savedAgent.getAgentCode() + "' for enterprise '" + enterprise.getName() + "'",
                enterprise
        );

        return new AgentEnrollResponse(
                savedAgent.getId(),
                enterprise.getId(),
                enterprise.getName(),
                enterprise.getOrganizationCode(),
                savedAgent.getAgentCode(),
                keyId,
                keyId + ":" + secret,
                savedAgent.getCollectionIntervalSec(),
                savedAgent.getHeartbeatIntervalSec()
        );
    }

    @Transactional
    public AgentSummaryResponse recordHeartbeat(AgentPrincipal principal, AgentHeartbeatRequest request) {
        EnterpriseAgent agent = loadAgent(principal);
        Enterprise enterprise = agent.getEnterprise();
        Instant now = Instant.now();

        if (request.getAgentVersion() != null && !request.getAgentVersion().isBlank()) {
            agent.setAgentVersion(request.getAgentVersion().trim());
        }
        agent.setLastHeartbeatAt(now);
        agent.setLocalBufferCount(Math.max(0, request.getLocalBufferCount()));
        agent.setCollectionFailures(Math.max(0L, request.getCollectionFailures()));
        agent.setTransmissionFailures(Math.max(0L, request.getTransmissionFailures()));

        boolean delayed = request.getLocalBufferCount() > 0 || request.getTransmissionFailures() > 0;
        if (delayed) {
            agent.setStatus(EnterpriseAgent.STATUS_SYNC_DELAYED);
            agent.setLastSyncError(request.getMessage() != null ? request.getMessage() : "Agent buffering or transmission retries active");
            if (Enterprise.STATUS_CONNECTED.equals(enterprise.getConnectionStatus())) {
                enterprise.setConnectionStatus(Enterprise.STATUS_SYNC_DELAYED);
                enterprise.setStatusReason("Agent reports buffered or delayed telemetry");
                enterpriseRepository.save(enterprise);
            }
        } else {
            agent.setStatus(EnterpriseAgent.STATUS_ONLINE);
            agent.setLastSyncError(null);
            if (Enterprise.STATUS_SYNC_DELAYED.equals(enterprise.getConnectionStatus())
                    && agent.getLastSuccessfulTelemetryAt() != null) {
                enterprise.setConnectionStatus(Enterprise.STATUS_CONNECTED);
                enterprise.setStatusReason("Agent heartbeat and sync healthy");
                enterpriseRepository.save(enterprise);
            }
        }

        EnterpriseAgent savedAgent = agentRepository.save(agent);

        AgentHeartbeat hb = new AgentHeartbeat();
        hb.setAgent(savedAgent);
        hb.setEnterprise(enterprise);
        hb.setAgentVersion(savedAgent.getAgentVersion());
        hb.setStatus(savedAgent.getStatus());
        hb.setLocalBufferCount(savedAgent.getLocalBufferCount());
        hb.setCollectionFailures(savedAgent.getCollectionFailures());
        hb.setTransmissionFailures(savedAgent.getTransmissionFailures());
        hb.setLastSuccessfulSyncAt(request.getLastSuccessfulSyncAt() != null
                ? request.getLastSuccessfulSyncAt()
                : savedAgent.getLastSuccessfulTelemetryAt());
        hb.setMessage(request.getMessage());
        heartbeatRepository.save(hb);

        return AgentSummaryResponse.from(savedAgent);
    }

    @Transactional
    public AgentTelemetryBatchResponse ingestTelemetryBatch(AgentPrincipal principal, AgentTelemetryBatchRequest request) {
        EnterpriseAgent agent = loadAgent(principal);
        Enterprise enterprise = agent.getEnterprise();

        if (batchRepository.existsByAgentIdAndClientBatchId(agent.getId(), request.getClientBatchId().trim())) {
            return new AgentTelemetryBatchResponse(
                    "DUPLICATE_IGNORED",
                    request.getClientBatchId(),
                    true,
                    request.getSamples().size(),
                    0,
                    0,
                    enterprise.getConnectionStatus(),
                    List.of()
            );
        }

        int received = request.getSamples().size();
        int accepted = 0;
        int rejected = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < received; i++) {
            AgentTelemetryBatchRequest.SampleDto sample = request.getSamples().get(i);
            try {
                MonitoredService svc = resolveOrCreateEnterpriseService(enterprise, sample);
                metricService.submitAgentMetric(
                        svc,
                        agent,
                        sample.getMetricName(),
                        sample.getMetricValue(),
                        sample.getUnit(),
                        sample.getRecordedAt()
                );
                accepted++;
            } catch (Exception ex) {
                rejected++;
                errors.add("Sample #" + i + " (" + sample.getServiceName() + "/" + sample.getMetricName() + "): " + ex.getMessage());
            }
        }

        AgentTelemetryBatch ledger = new AgentTelemetryBatch();
        ledger.setAgent(agent);
        ledger.setEnterprise(enterprise);
        ledger.setClientBatchId(request.getClientBatchId().trim());
        ledger.setRecordCount(received);
        ledger.setAcceptedCount(accepted);
        ledger.setRejectedCount(rejected);
        batchRepository.save(ledger);

        Instant now = Instant.now();
        agent.setLastHeartbeatAt(now);

        if (accepted > 0) {
            agent.setLastSuccessfulTelemetryAt(now);
            agent.setStatus(EnterpriseAgent.STATUS_ONLINE);
            agent.setLastSyncError(null);
            enterprise.setLastTelemetryAt(now);
            enterprise.setConnectionStatus(Enterprise.STATUS_CONNECTED);
            enterprise.setStatusReason("Verified telemetry active from agent '" + agent.getAgentCode() + "'");
        } else {
            agent.setStatus(EnterpriseAgent.STATUS_INTEGRATION_ERROR);
            agent.setLastSyncError(errors.isEmpty() ? "All telemetry samples failed validation" : errors.get(0));
            enterprise.setConnectionStatus(Enterprise.STATUS_INTEGRATION_ERROR);
            enterprise.setStatusReason("Telemetry validation failed for batch " + request.getClientBatchId());
        }
        agentRepository.save(agent);
        enterpriseRepository.save(enterprise);

        String batchStatus = rejected == 0 ? "SUCCESS" : (accepted > 0 ? "PARTIAL_SUCCESS" : "FAILED");
        return new AgentTelemetryBatchResponse(
                batchStatus,
                request.getClientBatchId(),
                false,
                received,
                accepted,
                rejected,
                enterprise.getConnectionStatus(),
                errors
        );
    }

    @Transactional(readOnly = true)
    public AgentConfigResponse getAgentConfig(AgentPrincipal principal) {
        EnterpriseAgent agent = loadAgent(principal);
        return new AgentConfigResponse(
                agent.getId(),
                agent.getEnterprise().getId(),
                agent.getAgentCode(),
                agent.getStatus(),
                agent.getEnterprise().getConnectionStatus(),
                agent.getCollectionIntervalSec(),
                agent.getHeartbeatIntervalSec(),
                SUPPORTED_METRICS,
                agent.isActive()
        );
    }

    @Transactional
    public AgentSummaryResponse reportStatus(AgentPrincipal principal, Map<String, Object> body) {
        EnterpriseAgent agent = loadAgent(principal);
        if (body != null) {
            if (body.get("status") instanceof String s && !s.isBlank()) {
                String normalized = s.trim().toUpperCase(Locale.ROOT);
                if (List.of(EnterpriseAgent.STATUS_ONLINE, EnterpriseAgent.STATUS_SYNC_DELAYED,
                        EnterpriseAgent.STATUS_INTEGRATION_ERROR, EnterpriseAgent.STATUS_DISCONNECTED).contains(normalized)) {
                    agent.setStatus(normalized);
                }
            }
            if (body.get("message") instanceof String msg) {
                agent.setLastSyncError(msg.length() > 500 ? msg.substring(0, 500) : msg);
            }
            if (body.get("agentVersion") instanceof String ver && !ver.isBlank()) {
                agent.setAgentVersion(ver.trim());
            }
        }
        agent.setLastHeartbeatAt(Instant.now());
        return AgentSummaryResponse.from(agentRepository.save(agent));
    }

    private EnterpriseAgent loadAgent(AgentPrincipal principal) {
        if (principal == null) {
            throw new BadCredentialsException("Agent principal missing");
        }
        return agentRepository.findByIdWithEnterprise(principal.agentId())
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found with id: " + principal.agentId()));
    }

    private MonitoredService resolveOrCreateEnterpriseService(
            Enterprise enterprise,
            AgentTelemetryBatchRequest.SampleDto sample) {
        String serviceName = sample.getServiceName().trim();
        return serviceRepository.findByEnterpriseIdAndNameIgnoreCase(enterprise.getId(), serviceName)
                .orElseGet(() -> {
                    MonitoredService svc = new MonitoredService();
                    svc.setEnterprise(enterprise);
                    svc.setName(serviceName);
                    svc.setServiceKey(serviceName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"));
                    String env = sample.getEnvironment() != null && sample.getEnvironment().trim().matches("^(?i)(DEVELOPMENT|STAGING|PRODUCTION)$")
                            ? sample.getEnvironment().trim().toUpperCase(Locale.ROOT)
                            : enterprise.getEnvironment();
                    svc.setEnvironment(env);
                    String endpoint = sample.getEndpointUrl() != null && sample.getEndpointUrl().trim().matches("^https?://.*")
                            ? sample.getEndpointUrl().trim()
                            : "http://" + svc.getServiceKey() + ".internal";
                    svc.setEndpointUrl(endpoint);
                    svc.setDescription("Auto-discovered via Spectra Monitoring Agent");
                    svc.setActive(true);
                    MonitoredService saved = serviceRepository.save(svc);
                    alertService.ensureDefaultRulesForService(saved);
                    return saved;
                });
    }
}
