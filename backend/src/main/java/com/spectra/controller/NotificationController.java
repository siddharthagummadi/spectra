package com.spectra.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spectra.dto.InAppNotificationResponse;
import com.spectra.entity.User;
import com.spectra.repository.UserRepository;
import com.spectra.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
@Validated
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationController(
            NotificationService notificationService,
            UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    private User resolveUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    @GetMapping
    public ResponseEntity<List<InAppNotificationResponse>> getNotifications(
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {

        User user = resolveUser(auth);
        List<InAppNotificationResponse> list = notificationService.getNotifications(user, limit);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(Authentication auth) {
        User user = resolveUser(auth);
        long count = notificationService.getUnreadCount(user);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(
            @PathVariable Long id,
            Authentication auth) {

        User user = resolveUser(auth);
        notificationService.markAsRead(id, user);
        return ResponseEntity.ok(Map.of("status", "SUCCESS"));
    }
}
