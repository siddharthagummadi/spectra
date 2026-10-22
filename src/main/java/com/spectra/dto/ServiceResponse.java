package com.spectra.dto;

import java.time.LocalDateTime;

import com.spectra.entity.MonitoredService;

public record ServiceResponse(
        Long id,
        String name,
        String description,
        String endpointUrl,
        String environment,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String ownerEmail) {

    public static ServiceResponse from(MonitoredService service) {
        String ownerEmail = service.getUser() != null ? service.getUser().getEmail() : null;
        return new ServiceResponse(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getEndpointUrl(),
                service.getEnvironment(),
                service.isActive(),
                service.getCreatedAt(),
                service.getUpdatedAt(),
                ownerEmail);
    }
}
