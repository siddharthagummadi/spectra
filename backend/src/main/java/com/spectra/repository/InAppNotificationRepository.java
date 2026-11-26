package com.spectra.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.spectra.entity.InAppNotification;

@Repository
public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {

    List<InAppNotification> findByRecipientRoleOrRecipientEmailOrderByCreatedAtDesc(
            String recipientRole, String recipientEmail, Pageable pageable);

    List<InAppNotification> findByRecipientEmailOrderByCreatedAtDesc(String recipientEmail, Pageable pageable);

    long countByRecipientRoleAndIsReadFalse(String recipientRole);

    long countByRecipientEmailAndIsReadFalse(String recipientEmail);
}
