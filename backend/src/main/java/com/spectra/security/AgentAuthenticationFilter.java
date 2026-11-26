package com.spectra.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.spectra.entity.AgentCredential;
import com.spectra.entity.Enterprise;
import com.spectra.entity.EnterpriseAgent;
import com.spectra.repository.AgentCredentialRepository;
import com.spectra.repository.EnterpriseAgentRepository;
import com.spectra.repository.EnterpriseRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authenticates standalone monitoring agents on /api/agent/** using per-agent
 * keyId:secret credentials (Bearer or X-Agent-Key header).
 */
public class AgentAuthenticationFilter extends OncePerRequestFilter {

    private final AgentCredentialRepository credentialRepository;
    private final EnterpriseAgentRepository agentRepository;
    private final EnterpriseRepository enterpriseRepository;

    public AgentAuthenticationFilter(
            AgentCredentialRepository credentialRepository,
            EnterpriseAgentRepository agentRepository,
            EnterpriseRepository enterpriseRepository) {
        this.credentialRepository = credentialRepository;
        this.agentRepository = agentRepository;
        this.enterpriseRepository = enterpriseRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/agent/") || "/api/agent/enroll".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String rawToken = extractToken(request);
        if (rawToken == null || !rawToken.contains(":")) {
            writeUnauthorized(response, "Missing or malformed agent credential");
            return;
        }

        int sep = rawToken.indexOf(':');
        String keyId = rawToken.substring(0, sep).trim();
        String secret = rawToken.substring(sep + 1).trim();
        if (keyId.isEmpty() || secret.isEmpty()) {
            writeUnauthorized(response, "Invalid agent credential format");
            return;
        }

        Optional<AgentCredential> credOpt = credentialRepository.findByKeyIdWithAgentAndEnterprise(keyId);
        if (credOpt.isEmpty()) {
            writeUnauthorized(response, "Unknown agent credential");
            return;
        }

        AgentCredential cred = credOpt.get();
        EnterpriseAgent agent = cred.getAgent();
        Enterprise enterprise = agent.getEnterprise();

        if (AgentCredential.STATUS_REVOKED.equals(cred.getStatus())
                || cred.getRevokedAt() != null
                || (cred.getExpiresAt() != null && cred.getExpiresAt().isBefore(Instant.now()))) {
            markAuthFailure(agent, enterprise, "Revoked or expired agent credential");
            writeUnauthorized(response, "Agent credential is revoked or expired");
            return;
        }

        if (!agent.isActive()) {
            writeUnauthorized(response, "Monitoring agent is deactivated");
            return;
        }

        if (Enterprise.STATUS_DISCONNECTED.equals(enterprise.getConnectionStatus())) {
            writeUnauthorized(response, "Enterprise connection is disconnected");
            return;
        }

        String presentedHash = sha256Hex(secret);
        if (!MessageDigest.isEqual(
                presentedHash.getBytes(StandardCharsets.UTF_8),
                cred.getSecretHash().getBytes(StandardCharsets.UTF_8))) {
            markAuthFailure(agent, enterprise, "Invalid agent secret presented");
            writeUnauthorized(response, "Invalid agent secret");
            return;
        }

        cred.setLastUsedAt(Instant.now());
        credentialRepository.save(cred);

        AgentPrincipal principal = new AgentPrincipal(
                agent.getId(),
                enterprise.getId(),
                agent.getAgentCode(),
                cred.getKeyId()
        );

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ENTERPRISE_AGENT"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void markAuthFailure(EnterpriseAgent agent, Enterprise enterprise, String reason) {
        try {
            agent.setStatus(EnterpriseAgent.STATUS_AUTHENTICATION_FAILED);
            agent.setLastSyncError(reason);
            agentRepository.save(agent);
            enterprise.setConnectionStatus(Enterprise.STATUS_AUTHENTICATION_FAILED);
            enterprise.setStatusReason(reason);
            enterpriseRepository.save(enterprise);
        } catch (Exception ignored) {
        }
    }

    private String extractToken(HttpServletRequest request) {
        String agentHeader = request.getHeader("X-Agent-Key");
        if (agentHeader != null && !agentHeader.isBlank()) {
            return agentHeader.trim();
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authHeader.substring(7).trim();
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}");
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
