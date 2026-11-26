package com.spectra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.spectra.entity.AgentEnrollment;

public interface AgentEnrollmentRepository extends JpaRepository<AgentEnrollment, Long> {

    @Query("SELECT e FROM AgentEnrollment e JOIN FETCH e.enterprise WHERE e.tokenHash = :tokenHash")
    Optional<AgentEnrollment> findByTokenHashWithEnterprise(@Param("tokenHash") String tokenHash);

    List<AgentEnrollment> findByEnterpriseIdOrderByCreatedAtDesc(Long enterpriseId);

    List<AgentEnrollment> findByEnterpriseIdAndStatus(Long enterpriseId, String status);
}
