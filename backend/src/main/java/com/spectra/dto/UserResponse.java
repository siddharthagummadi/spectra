package com.spectra.dto;

import com.spectra.entity.Enterprise;
import com.spectra.entity.Role;
import com.spectra.entity.User;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        Role role,
        Long enterpriseId,
        String enterpriseName,
        String enterpriseOrganizationCode,
        String enterpriseStatus) {

    public UserResponse(Long id, String fullName, String email, Role role) {
        this(id, fullName, email, role, null, null, null, null);
    }

    public static UserResponse from(User user) {
        Enterprise ent = user.getEnterprise();
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                ent != null ? ent.getId() : null,
                ent != null ? ent.getName() : null,
                ent != null ? ent.getOrganizationCode() : null,
                ent != null ? ent.getConnectionStatus() : null);
    }
}
