package com.spectra.dto;

import java.util.List;

public record PlatformOverviewResponse(
        long totalEnterprises,
        long connectedEnterprises,
        long pendingEnterprises,
        long degradedOrDelayedEnterprises,
        long disconnectedEnterprises,
        long totalMonitoredServices,
        long activeAlerts,
        long openIncidents,
        long totalAgents,
        long onlineAgents,
        long delayedAgents,
        long disconnectedAgents,
        List<EnterpriseSummaryResponse> enterprises
) {
}
