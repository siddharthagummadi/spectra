package com.spectra.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.entity.AuditLog;
import com.spectra.entity.Enterprise;
import com.spectra.repository.AuditLogRepository;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Record an audit log event in an isolated transaction so that it is persisted
     * even if subsequent business logic rolls back or completes independently.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog recordAction(String action, String performedBy, String details) {
        return recordAction(action, performedBy, details, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog recordAction(String action, String performedBy, String details, Enterprise enterprise) {
        try {
            AuditLog auditLog = new AuditLog(
                    action,
                    performedBy != null && !performedBy.trim().isEmpty() ? performedBy.trim() : "SYSTEM",
                    details
            );
            auditLog.setEnterprise(enterprise);
            AuditLog saved = auditLogRepository.save(auditLog);
            log.info("AUDIT EVENT: [{}] by [{}]: {}", action, performedBy, details);
            return saved;
        } catch (Exception ex) {
            log.error("Failed to record audit log for action '{}' by '{}': {}", action, performedBy, ex.getMessage(), ex);
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getRecentAuditLogs(int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit));
    }
}
