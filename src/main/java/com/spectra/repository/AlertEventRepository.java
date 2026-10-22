package com.spectra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.spectra.entity.AlertEvent;

import jakarta.persistence.LockModeType;

public interface AlertEventRepository extends JpaRepository<AlertEvent, Long> {

    List<AlertEvent> findAllByOrderByTriggeredAtDescIdDesc();

    List<AlertEvent> findByServiceUserIdOrderByTriggeredAtDescIdDesc(Long userId);

    List<AlertEvent> findByStatusOrderByTriggeredAtDescIdDesc(String status);

    List<AlertEvent> findByServiceUserIdAndStatusOrderByTriggeredAtDescIdDesc(Long userId, String status);

    List<AlertEvent> findByServiceIdOrderByTriggeredAtDescIdDesc(Long serviceId);

    List<AlertEvent> findByServiceIdAndServiceUserIdOrderByTriggeredAtDescIdDesc(Long serviceId, Long userId);

    List<AlertEvent> findByServiceIdAndStatusOrderByTriggeredAtDescIdDesc(Long serviceId, String status);

    List<AlertEvent> findByServiceIdAndServiceUserIdAndStatusOrderByTriggeredAtDescIdDesc(Long serviceId, Long userId, String status);

    Optional<AlertEvent> findByIdAndServiceUserId(Long id, Long userId);

    Optional<AlertEvent> findFirstByRuleIdAndStatusIn(Long ruleId, List<String> statuses);

    List<AlertEvent> findByRuleIdAndStatusIn(Long ruleId, List<String> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AlertEvent a WHERE a.id = :id")
    Optional<AlertEvent> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AlertEvent a WHERE a.id = :id AND a.service.user.id = :userId")
    Optional<AlertEvent> findByIdAndServiceUserIdForUpdate(@Param("id") Long id, @Param("userId") Long userId);
}
