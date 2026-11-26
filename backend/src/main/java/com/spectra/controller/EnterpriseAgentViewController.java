package com.spectra.controller;

import java.util.Collections;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spectra.dto.AgentSummaryResponse;
import com.spectra.entity.Enterprise;
import com.spectra.entity.User;
import com.spectra.repository.EnterpriseAgentRepository;
import com.spectra.service.TenantContextService;

/**
 * Allows authenticated Enterprise Users to inspect monitoring agents within their own enterprise.
 */
@RestController
@RequestMapping("/api/enterprise")
public class EnterpriseAgentViewController {

    private final TenantContextService tenantContextService;
    private final EnterpriseAgentRepository agentRepository;

    public EnterpriseAgentViewController(
            TenantContextService tenantContextService,
            EnterpriseAgentRepository agentRepository) {
        this.tenantContextService = tenantContextService;
        this.agentRepository = agentRepository;
    }

    @GetMapping("/agents")
    public ResponseEntity<List<AgentSummaryResponse>> getMyEnterpriseAgents() {
        User user = tenantContextService.getCurrentUser();
        if (user == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        Enterprise enterprise = tenantContextService.resolveOrProvisionEnterprise(user);
        if (enterprise == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<AgentSummaryResponse> agents = agentRepository.findByEnterpriseIdOrderByCreatedAtDesc(enterprise.getId())
                .stream()
                .map(AgentSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(agents);
    }
}
