package com.spectra.agent;

import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime configuration for the standalone Spectra Enterprise Monitoring Agent.
 */
public class AgentConfig {

    private final String serverUrl;
    private final String enrollToken;
    private final String agentCode;
    private final String serviceName;
    private final String environment;
    private final int collectionIntervalSec;
    private final int heartbeatIntervalSec;
    private final Path stateDir;
    private final Map<String, String> healthTargets;
    private final String agentVersion = "1.0.0";

    public AgentConfig(
            String serverUrl,
            String enrollToken,
            String agentCode,
            String serviceName,
            String environment,
            int collectionIntervalSec,
            int heartbeatIntervalSec,
            Path stateDir,
            Map<String, String> healthTargets) {
        this.serverUrl = serverUrl.endsWith("/") ? serverUrl.substring(0, serverUrl.length() - 1) : serverUrl;
        this.enrollToken = enrollToken;
        this.agentCode = agentCode;
        this.serviceName = serviceName;
        this.environment = environment;
        this.collectionIntervalSec = Math.max(5, collectionIntervalSec);
        this.heartbeatIntervalSec = Math.max(5, heartbeatIntervalSec);
        this.stateDir = stateDir;
        this.healthTargets = healthTargets;
    }

    public static AgentConfig fromArgs(String[] args) {
        String serverUrl = envOrDefault("SPECTRA_SERVER_URL", "http://localhost:8080");
        String enrollToken = envOrDefault("SPECTRA_ENROLL_TOKEN", "");
        String agentCode = envOrDefault("SPECTRA_AGENT_CODE", defaultHostAgentCode());
        String serviceName = envOrDefault("SPECTRA_SERVICE_NAME", "Host-Infrastructure");
        String environment = envOrDefault("SPECTRA_ENVIRONMENT", "PRODUCTION");
        int collectionSec = Integer.parseInt(envOrDefault("SPECTRA_COLLECTION_INTERVAL_SEC", "30"));
        int heartbeatSec = Integer.parseInt(envOrDefault("SPECTRA_HEARTBEAT_INTERVAL_SEC", "30"));
        Path stateDir = Paths.get(envOrDefault("SPECTRA_STATE_DIR", ".spectra-agent"));
        String targetsRaw = envOrDefault("SPECTRA_TARGETS", "");

        if (args != null) {
            for (String arg : args) {
                if (arg.startsWith("--server-url=")) serverUrl = arg.substring("--server-url=".length());
                else if (arg.startsWith("--enroll-token=")) enrollToken = arg.substring("--enroll-token=".length());
                else if (arg.startsWith("--agent-code=")) agentCode = arg.substring("--agent-code=".length());
                else if (arg.startsWith("--service-name=")) serviceName = arg.substring("--service-name=".length());
                else if (arg.startsWith("--environment=")) environment = arg.substring("--environment=".length());
                else if (arg.startsWith("--interval-sec=")) collectionSec = Integer.parseInt(arg.substring("--interval-sec=".length()));
                else if (arg.startsWith("--heartbeat-sec=")) heartbeatSec = Integer.parseInt(arg.substring("--heartbeat-sec=".length()));
                else if (arg.startsWith("--state-dir=")) stateDir = Paths.get(arg.substring("--state-dir=".length()));
                else if (arg.startsWith("--targets=")) targetsRaw = arg.substring("--targets=".length());
            }
        }

        Map<String, String> targets = new LinkedHashMap<>();
        if (targetsRaw != null && !targetsRaw.isBlank()) {
            for (String entry : targetsRaw.split(",")) {
                int idx = entry.indexOf('=');
                if (idx > 0 && idx < entry.length() - 1) {
                    targets.put(entry.substring(0, idx).trim(), entry.substring(idx + 1).trim());
                }
            }
        }

        return new AgentConfig(serverUrl, enrollToken, agentCode, serviceName, environment,
                collectionSec, heartbeatSec, stateDir, targets);
    }

    private static String envOrDefault(String key, String fallback) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val.trim() : fallback;
    }

    private static String defaultHostAgentCode() {
        try {
            return "agent-" + InetAddress.getLocalHost().getHostName().toLowerCase().replaceAll("[^a-z0-9-]", "-");
        } catch (Exception e) {
            return "agent-local-01";
        }
    }

    public String getServerUrl() { return serverUrl; }
    public String getEnrollToken() { return enrollToken; }
    public String getAgentCode() { return agentCode; }
    public String getServiceName() { return serviceName; }
    public String getEnvironment() { return environment; }
    public int getCollectionIntervalSec() { return collectionIntervalSec; }
    public int getHeartbeatIntervalSec() { return heartbeatIntervalSec; }
    public Path getStateDir() { return stateDir; }
    public Map<String, String> getHealthTargets() { return healthTargets; }
    public String getAgentVersion() { return agentVersion; }
}
