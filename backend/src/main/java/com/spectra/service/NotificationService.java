package com.spectra.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.dto.InAppNotificationResponse;
import com.spectra.entity.InAppNotification;
import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.InAppNotificationRepository;

@Service
public class NotificationService {

    private final InAppNotificationRepository repository;

    public NotificationService(InAppNotificationRepository repository) {
        this.repository = repository;
    }

    private boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    @Transactional(readOnly = true)
    public List<InAppNotificationResponse> getNotifications(User user, int limit) {
        if (user == null) return List.of();
        int safeLimit = Math.max(1, Math.min(50, limit));

        List<InAppNotification> list;
        if (isAdmin(user)) {
            list = repository.findByRecipientRoleOrRecipientEmailOrderByCreatedAtDesc("ADMIN", user.getEmail(), PageRequest.of(0, safeLimit));
        } else {
            list = repository.findByRecipientEmailOrderByCreatedAtDesc(user.getEmail(), PageRequest.of(0, safeLimit));
        }

        return list.stream().map(InAppNotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(User user) {
        if (user == null) return 0;
        if (isAdmin(user)) {
            return repository.countByRecipientRoleAndIsReadFalse("ADMIN") + repository.countByRecipientEmailAndIsReadFalse(user.getEmail());
        }
        return repository.countByRecipientEmailAndIsReadFalse(user.getEmail());
    }

    @Transactional
    public void markAsRead(Long id, User user) {
        InAppNotification n = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        n.setRead(true);
        repository.save(n);
    }
}
