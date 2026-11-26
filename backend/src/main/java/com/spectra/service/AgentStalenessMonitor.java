package com.spectra.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.entity.Enterprise;
import com.spectra.entity.EnterpriseAgent;
import com.spectra.repository.EnterpriseAgentRepository;
import com.spectra.repository.EnterpriseRepository;

/**
 * Evaluates agent heartbeat and telemetry freshness so missing telemetry
 * is explicitly surfaced as DISCONNECTED or SYNC_DELAYED rather than healthy.
 */
@Service
public class AgentStalenessMonitor {

    private static final Logger log = LoggerFactory.getLogger(AgentStalenessMonitor.class);

    private final EnterpriseAgentRepository agentRepository;
    private final EnterpriseRepository enterpriseRepository;

    public AgentStalenessMonitor(
            EnterpriseAgentRepository agentRepository,
            EnterpriseRepository enterpriseRepository) {
        this.agentRepository = agentRepository;
        this.enterpriseRepository = enterpriseRepository;
    }

    @Scheduled(fixedDelayString = "${spectra.agent.staleness-check-ms:30000}")
    @Transactional
    public void evaluateAgentStaleness() {
        evaluateAt(Instant.now());
    }

    @Transactional
    public void evaluateAt(Instant now) {
        List<EnterpriseAgent> activeAgents = agentRepository.findByActiveTrue();
        for (EnterpriseAgent agent : activeAgents) {
            Enterprise enterprise = agent.getEnterprise();
            if (enterprise == null || Enterprise.STATUS_PENDING.equals(enterprise.getConnectionStatus())) {
                continue;
            }

            long heartbeatDeadlineSec = Math.max(60L, agent.getHeartbeatIntervalSec() * 3L);
            long telemetryDeadlineSec = Math.max(90L, agent.getCollectionIntervalSec() * 3L);

            boolean heartbeatMissing = agent.getLastHeartbeatAt() == null
                    || agent.getLastHeartbeatAt().plusSeconds(heartbeatDeadlineSec).isBefore(now);

            if (heartbeatMissing) {
                if (!EnterpriseAgent.STATUS_DISCONNECTED.equals(agent.getStatus())) {
                    agent.setStatus(EnterpriseAgent.STATUS_DISCONNECTED);
                    agent.setLastSyncError("Heartbeat timeout exceeded (" + heartbeatDeadlineSec + "s); telemetry stale");
                    agentRepository.save(agent);
                    log.warn("Marked agent {} (enterprise={}) DISCONNECTED due to missed heartbeat",
                            agent.getAgentCode(), enterprise.getName());
                }
                if (!Enterprise.STATUS_DISCONNECTED.equals(enterprise.getConnectionStatus())) {
                    enterprise.setConnectionStatus(Enterprise.STATUS_DISCONNECTED);
                    enterprise.setStatusReason("Monitoring agent stopped reporting; telemetry stale");
                    enterpriseRepository.save(enterprise);
                }
                continue;
            }

            boolean telemetryDelayed = agent.getLocalBufferCount() > 0
                    || agent.getLastSuccessfulTelemetryAt() == null
                    || agent.getLastSuccessfulTelemetryAt().plusSeconds(telemetryDeadlineSec).isBefore(now);

            if (telemetryDelayed && EnterpriseAgent.STATUS_ONLINE.equals(agent.getStatus())) {
                agent.setStatus(EnterpriseAgent.STATUS_SYNC_DELAYED);
                agent.setLastSyncError("Heartbeat active, but telemetry synchronization delayed beyond " + telemetryDeadlineSec + "s");
                agentRepository.save(agent);
                if (Enterprise.STATUS_CONNECTED.equals(enterprise.getConnectionStatus())) {
                    enterprise.setConnectionStatus(Enterprise.STATUS_SYNC_DELAYED);
                    enterprise.setStatusReason("Agent online but telemetry sync delayed");
                    enterpriseRepository.save(enterprise);
                }
            }
        }
    }
}
