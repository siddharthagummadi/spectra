package com.spectra.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateIntegrationRequest {

    @NotBlank(message = "Enterprise name is required")
    @Size(max = 150)
    private String enterpriseName;

    @NotBlank(message = "Organization name is required")
    @Size(max = 150)
    private String organizationName;

    @NotBlank(message = "Environment is required")
    private String environment; // PRODUCTION, STAGING, DEVELOPMENT

    @NotBlank(message = "Integration type is required")
    private String integrationType; // DATADOG, PROMETHEUS, NEW_RELIC, CLOUDWATCH, CUSTOM_WEBHOOK, REST_API

    @NotBlank(message = "API endpoint URL is required")
    @Size(max = 500)
    private String apiEndpoint;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Must be a valid email address")
    private String contactEmail;

    @NotBlank(message = "Contact name is required")
    @Size(max = 100)
    private String contactName;

    @Size(max = 1000)
    private String connectionPurpose;

    @Size(max = 500)
    private String credentials; // Will be securely encrypted and never returned in plaintext

    public CreateIntegrationRequest() {
    }

    public String getEnterpriseName() { return enterpriseName; }
    public void setEnterpriseName(String enterpriseName) { this.enterpriseName = enterpriseName; }

    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getIntegrationType() { return integrationType; }
    public void setIntegrationType(String integrationType) { this.integrationType = integrationType; }

    public String getApiEndpoint() { return apiEndpoint; }
    public void setApiEndpoint(String apiEndpoint) { this.apiEndpoint = apiEndpoint; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getConnectionPurpose() { return connectionPurpose; }
    public void setConnectionPurpose(String connectionPurpose) { this.connectionPurpose = connectionPurpose; }

    public String getCredentials() { return credentials; }
    public void setCredentials(String credentials) { this.credentials = credentials; }
}
