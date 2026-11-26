package com.spectra.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.spectra.entity.AgentHeartbeat;

public interface AgentHeartbeatRepository extends JpaRepository<AgentHeartbeat, Long> {

    List<AgentHeartbeat> findByAgentIdOrderByReceivedAtDesc(Long agentId, Pageable pageable);

    List<AgentHeartbeat> findByEnterpriseIdOrderByReceivedAtDesc(Long enterpriseId, Pageable pageable);
}
