package com.spectra.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.spectra.entity.AgentTelemetryBatch;

public interface AgentTelemetryBatchRepository extends JpaRepository<AgentTelemetryBatch, Long> {

    boolean existsByAgentIdAndClientBatchId(Long agentId, String clientBatchId);

    Optional<AgentTelemetryBatch> findByAgentIdAndClientBatchId(Long agentId, String clientBatchId);
}
