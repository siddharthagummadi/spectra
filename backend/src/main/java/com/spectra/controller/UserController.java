package com.spectra.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spectra.dto.RegisterRequest;
import com.spectra.dto.UserResponse;
import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.service.AuditLogService;
import com.spectra.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    private final UserService userService;
    private final AuditLogService auditLogService;

    @Autowired
    public UserController(UserService userService, @Autowired(required = false) AuditLogService auditLogService) {
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    public UserController(UserService userService) {
        this(userService, null);
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        User user = (request.getOrganizationCode() != null && !request.getOrganizationCode().isBlank())
                ? userService.registerUser(
                        request.getFullName(),
                        request.getEmail(),
                        request.getPassword(),
                        request.getOrganizationCode())
                : userService.registerUser(
                        request.getFullName(),
                        request.getEmail(),
                        request.getPassword());

        if (auditLogService != null) {
            auditLogService.recordAction("USER_REGISTERED", user.getEmail(), "Registered new user: " + user.getEmail());
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(UserResponse.from(user));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        User user = userService.getUserByEmail(authentication.getName());
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<UserResponse> updateUserRole(
            @PathVariable Long id,
            @RequestParam Role role,
            Authentication authentication) {
        User user = userService.updateUserRole(id, role);
        if (auditLogService != null) {
            auditLogService.recordAction(
                    "USER_ROLE_UPDATED",
                    authentication != null ? authentication.getName() : "ADMIN",
                    "Updated role of user id=" + id + " to " + role
            );
        }
        return ResponseEntity.ok(UserResponse.from(user));
    }
}
