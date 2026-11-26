package com.spectra.dto;

import java.util.List;

public record EnterpriseDetailDashboardResponse(
        EnterpriseSummaryResponse enterprise,
        List<ServiceResponse> services,
        List<MetricResponse> recentMetrics,
        List<AlertEventResponse> alerts,
        List<AnomalyEventResponse> anomalies,
        List<IncidentResponse> incidents,
        List<AgentSummaryResponse> agents,
        boolean readOnly
) {
}
