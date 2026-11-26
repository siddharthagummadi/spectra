package com.spectra.dto;

import com.spectra.entity.IncidentAiAnalysis;

/**
 * Composite response contract for incident AI diagnostics.
 *
 * Exposes the newest execution attempt (regardless of status, including PENDING,
 * PROCESSING, or FAILED) alongside the last verified successful COMPLETED diagnosis.
 */
public record IncidentAiDiagnosticsResponse(
        Long incidentId,
        IncidentAiAnalysisResponse latestExecution,
        IncidentAiAnalysisResponse lastSuccessful
) {

    public static IncidentAiDiagnosticsResponse of(
            Long incidentId,
            IncidentAiAnalysis latestExecution,
            IncidentAiAnalysis lastSuccessful) {
        return new IncidentAiDiagnosticsResponse(
                incidentId,
                latestExecution != null ? IncidentAiAnalysisResponse.from(latestExecution) : null,
                lastSuccessful != null ? IncidentAiAnalysisResponse.from(lastSuccessful) : null
        );
    }
}
