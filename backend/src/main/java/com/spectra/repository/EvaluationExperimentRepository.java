package com.spectra.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.spectra.entity.EvaluationExperiment;

@Repository
public interface EvaluationExperimentRepository extends JpaRepository<EvaluationExperiment, Long> {

    List<EvaluationExperiment> findAllByOrderByCreatedAtDesc();

    List<EvaluationExperiment> findByAlgorithmOrderByCreatedAtDesc(String algorithm);

    List<EvaluationExperiment> findByDatasetNameOrderByCreatedAtDesc(String datasetName);

    List<EvaluationExperiment> findByBatchIdOrderByCreatedAtDesc(Long batchId);
}
