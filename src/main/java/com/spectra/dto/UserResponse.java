
package com.spectra.dto;

import com.spectra.entity.Role;
import com.spectra.entity.User;

public record UserResponse(
        Long id,
        String fullName,
        String email,
        Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole());
    }
}