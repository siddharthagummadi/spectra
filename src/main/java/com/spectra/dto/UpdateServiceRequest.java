package com.spectra.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateServiceRequest {

    @NotBlank(message = "Service name is required")
    @Size(max = 100, message = "Service name must not exceed 100 characters")
    private String name;

    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;

    @NotBlank(message = "Endpoint URL is required")
    @Pattern(
        regexp = "^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$",
        message = "Endpoint URL must be a valid HTTP or HTTPS URL"
    )
    private String endpointUrl;

    @NotBlank(message = "Environment is required")
    @Pattern(
        regexp = "^(?i)(DEVELOPMENT|STAGING|PRODUCTION)$",
        message = "Environment must be DEVELOPMENT, STAGING, or PRODUCTION"
    )
    private String environment;

    public UpdateServiceRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }
}
