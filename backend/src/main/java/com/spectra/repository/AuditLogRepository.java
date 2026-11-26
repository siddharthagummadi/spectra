package com.spectra.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.spectra.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AuditLog> findByPerformedByOrderByCreatedAtDesc(String performedBy, Pageable pageable);

    List<AuditLog> findByEnterpriseIdOrderByCreatedAtDesc(Long enterpriseId, Pageable pageable);
}
