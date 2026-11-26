package com.spectra.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AgentEnrollRequest {

    @NotBlank(message = "Enrollment token is required")
    private String enrollmentToken;

    @NotBlank(message = "Agent code is required")
    @Size(max = 100)
    private String agentCode;

    @NotBlank(message = "Hostname is required")
    @Size(max = 150)
    private String hostname;

    @Size(max = 150)
    private String osInfo;

    @Size(max = 40)
    private String agentVersion = "1.0.0";

    public String getEnrollmentToken() {
        return enrollmentToken;
    }

    public void setEnrollmentToken(String enrollmentToken) {
        this.enrollmentToken = enrollmentToken;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getOsInfo() {
        return osInfo;
    }

    public void setOsInfo(String osInfo) {
        this.osInfo = osInfo;
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public void setAgentVersion(String agentVersion) {
        this.agentVersion = agentVersion;
    }
}
