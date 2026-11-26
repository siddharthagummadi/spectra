package com.spectra.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateEnterpriseRequest {

    @NotBlank(message = "Enterprise name is required")
    @Size(max = 150)
    private String name;

    @NotBlank(message = "Organization code is required")
    @Size(max = 64)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Organization code may contain letters, numbers, hyphens, and underscores only")
    private String organizationCode;

    @NotBlank(message = "Environment is required")
    @Pattern(regexp = "^(?i)(DEVELOPMENT|STAGING|PRODUCTION)$", message = "Environment must be DEVELOPMENT, STAGING, or PRODUCTION")
    private String environment = "PRODUCTION";

    @Size(max = 100)
    private String contactName;

    @Email(message = "Enter a valid contact email")
    @Size(max = 150)
    private String contactEmail;

    private Integer tokenTtlMinutes = 60;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrganizationCode() {
        return organizationCode;
    }

    public void setOrganizationCode(String organizationCode) {
        this.organizationCode = organizationCode;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public Integer getTokenTtlMinutes() {
        return tokenTtlMinutes;
    }

    public void setTokenTtlMinutes(Integer tokenTtlMinutes) {
        this.tokenTtlMinutes = tokenTtlMinutes;
    }
}
