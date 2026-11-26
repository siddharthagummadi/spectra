package com.spectra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.spectra.entity.EnterpriseAgent;

public interface EnterpriseAgentRepository extends JpaRepository<EnterpriseAgent, Long> {

    List<EnterpriseAgent> findByEnterpriseIdOrderByCreatedAtDesc(Long enterpriseId);

    List<EnterpriseAgent> findAllByOrderByCreatedAtDesc();

    Optional<EnterpriseAgent> findByEnterpriseIdAndAgentCode(Long enterpriseId, String agentCode);

    Optional<EnterpriseAgent> findByIdAndEnterpriseId(Long id, Long enterpriseId);

    @Query("SELECT a FROM EnterpriseAgent a JOIN FETCH a.enterprise WHERE a.id = :id")
    Optional<EnterpriseAgent> findByIdWithEnterprise(@Param("id") Long id);

    @Query("SELECT a FROM EnterpriseAgent a JOIN FETCH a.enterprise ORDER BY a.createdAt DESC")
    List<EnterpriseAgent> findAllWithEnterpriseOrderByCreatedAtDesc();

    List<EnterpriseAgent> findByActiveTrue();

    long countByEnterpriseIdAndActiveTrue(Long enterpriseId);

    long countByStatusAndActiveTrue(String status);
}
