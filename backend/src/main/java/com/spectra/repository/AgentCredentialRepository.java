package com.spectra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.spectra.entity.AgentCredential;

public interface AgentCredentialRepository extends JpaRepository<AgentCredential, Long> {

    @Query("SELECT c FROM AgentCredential c JOIN FETCH c.agent a JOIN FETCH a.enterprise WHERE c.keyId = :keyId")
    Optional<AgentCredential> findByKeyIdWithAgentAndEnterprise(@Param("keyId") String keyId);

    List<AgentCredential> findByAgentIdAndStatusIn(Long agentId, List<String> statuses);

    List<AgentCredential> findByAgentIdOrderByIssuedAtDesc(Long agentId);
}
