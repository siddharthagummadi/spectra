package com.spectra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.spectra.entity.IngestionBatch;

@Repository
public interface IngestionBatchRepository extends JpaRepository<IngestionBatch, Long> {

    Optional<IngestionBatch> findByBatchKey(String batchKey);

    List<IngestionBatch> findByDeletedAtIsNullOrderByCreatedAtDesc();

    List<IngestionBatch> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    Optional<IngestionBatch> findByIdAndDeletedAtIsNull(Long id);

    Optional<IngestionBatch> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    List<IngestionBatch> findByEnterpriseIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long enterpriseId);

    Optional<IngestionBatch> findByIdAndEnterpriseIdAndDeletedAtIsNull(Long id, Long enterpriseId);
}
