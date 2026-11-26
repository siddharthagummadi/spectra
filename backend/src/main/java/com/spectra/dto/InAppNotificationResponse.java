package com.spectra.dto;

import java.time.Instant;

import com.spectra.entity.InAppNotification;

public class InAppNotificationResponse {

    private Long id;
    private String recipientRole;
    private String recipientEmail;
    private String title;
    private String message;
    private String type;
    private boolean isRead;
    private Instant createdAt;

    public InAppNotificationResponse() {
    }

    public static InAppNotificationResponse from(InAppNotification notification) {
        InAppNotificationResponse res = new InAppNotificationResponse();
        res.setId(notification.getId());
        res.setRecipientRole(notification.getRecipientRole());
        res.setRecipientEmail(notification.getRecipientEmail());
        res.setTitle(notification.getTitle());
        res.setMessage(notification.getMessage());
        res.setType(notification.getType());
        res.setRead(notification.isRead());
        res.setCreatedAt(notification.getCreatedAt());
        return res;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRecipientRole() { return recipientRole; }
    public void setRecipientRole(String recipientRole) { this.recipientRole = recipientRole; }

    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
