package com.spectra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.spectra.entity.IncidentAiAnalysis;

public interface IncidentAiAnalysisRepository extends JpaRepository<IncidentAiAnalysis, Long> {

    List<IncidentAiAnalysis> findByIncidentIdOrderByVersionDesc(Long incidentId);

    Optional<IncidentAiAnalysis> findFirstByIncidentIdOrderByVersionDesc(Long incidentId);

    Optional<IncidentAiAnalysis> findFirstByIncidentIdAndStatusOrderByVersionDesc(Long incidentId, String status);

    boolean existsByIncidentIdAndStatusIn(Long incidentId, List<String> statuses);

    Optional<IncidentAiAnalysis> findFirstByIncidentIdAndStatusInOrderByVersionDesc(Long incidentId, List<String> statuses);
}
