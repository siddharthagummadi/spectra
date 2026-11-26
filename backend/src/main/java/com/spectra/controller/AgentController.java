package com.spectra.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.spectra.dto.AgentConfigResponse;
import com.spectra.dto.AgentEnrollRequest;
import com.spectra.dto.AgentEnrollResponse;
import com.spectra.dto.AgentHeartbeatRequest;
import com.spectra.dto.AgentSummaryResponse;
import com.spectra.dto.AgentTelemetryBatchRequest;
import com.spectra.dto.AgentTelemetryBatchResponse;
import com.spectra.security.AgentPrincipal;
import com.spectra.service.AgentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/agent")
@Validated
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    private AgentPrincipal resolveAgent(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof AgentPrincipal principal) {
            return principal;
        }
        return null;
    }

    @PostMapping("/enroll")
    public ResponseEntity<AgentEnrollResponse> enroll(@Valid @RequestBody AgentEnrollRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentService.enroll(request));
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<AgentSummaryResponse> heartbeat(
            @RequestBody(required = false) AgentHeartbeatRequest request,
            Authentication auth) {
        AgentHeartbeatRequest req = request != null ? request : new AgentHeartbeatRequest();
        return ResponseEntity.ok(agentService.recordHeartbeat(resolveAgent(auth), req));
    }

    @PostMapping("/telemetry/batch")
    public ResponseEntity<AgentTelemetryBatchResponse> ingestBatch(
            @Valid @RequestBody AgentTelemetryBatchRequest request,
            Authentication auth) {
        AgentTelemetryBatchResponse response = agentService.ingestTelemetryBatch(resolveAgent(auth), request);
        HttpStatus status = "FAILED".equals(response.status())
                ? HttpStatus.BAD_REQUEST
                : ("DUPLICATE_IGNORED".equals(response.status()) ? HttpStatus.OK : HttpStatus.CREATED);
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/config")
    public ResponseEntity<AgentConfigResponse> getConfig(Authentication auth) {
        return ResponseEntity.ok(agentService.getAgentConfig(resolveAgent(auth)));
    }

    @PostMapping("/status")
    public ResponseEntity<AgentSummaryResponse> reportStatus(
            @RequestBody(required = false) Map<String, Object> body,
            Authentication auth) {
        return ResponseEntity.ok(agentService.reportStatus(resolveAgent(auth), body));
    }
}
