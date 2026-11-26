package com.spectra.security;

/**
 * Authenticated principal representing a validated Enterprise Monitoring Agent.
 * Strictly isolated from human UserDetails principals.
 */
public record AgentPrincipal(
        Long agentId,
        Long enterpriseId,
        String agentCode,
        String keyId
) {
}
