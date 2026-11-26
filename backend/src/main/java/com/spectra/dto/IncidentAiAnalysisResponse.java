package com.spectra.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.spectra.entity.IncidentAiAnalysis;

public record IncidentAiAnalysisResponse(
        Long id,
        Long incidentId,
        String status,
        String primaryRootCause,
        String failureCategory,
        Double confidenceScore,
        String primaryEvidence,
        List<Map<String, Object>> rankedCauses,
        List<String> contributingFactors,
        List<Map<String, Object>> recommendedRunbooks,
        String failureReason,
        Integer version,
        String executionId,
        Instant createdAt,
        Instant completedAt,
        Instant updatedAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static IncidentAiAnalysisResponse from(IncidentAiAnalysis entity) {
        if (entity == null) {
            return null;
        }

        List<Map<String, Object>> rankedCauses = parseJsonListMap(entity.getRankedCausesJson());
        List<String> contributingFactors = parseJsonListString(entity.getContributingFactorsJson());
        List<Map<String, Object>> recommendedRunbooks = parseJsonListMap(entity.getRecommendedRunbooksJson());

        return new IncidentAiAnalysisResponse(
                entity.getId(),
                entity.getIncident() != null ? entity.getIncident().getId() : null,
                entity.getStatus(),
                entity.getPrimaryRootCause(),
                entity.getFailureCategory(),
                entity.getConfidenceScore(),
                entity.getPrimaryEvidence(),
                rankedCauses,
                contributingFactors,
                recommendedRunbooks,
                entity.getFailureReason(),
                entity.getVersion(),
                entity.getExecutionId(),
                entity.getCreatedAt(),
                entity.getCompletedAt(),
                entity.getUpdatedAt()
        );
    }

    private static List<Map<String, Object>> parseJsonListMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private static List<String> parseJsonListString(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }
}
