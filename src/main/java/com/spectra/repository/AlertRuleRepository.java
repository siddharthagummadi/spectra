package com.spectra.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.spectra.entity.AlertRule;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findByServiceId(Long serviceId);

    List<AlertRule> findByServiceUserId(Long userId);

    List<AlertRule> findByServiceIdAndServiceUserId(Long serviceId, Long userId);

    Optional<AlertRule> findByIdAndServiceUserId(Long id, Long userId);

    List<AlertRule> findByServiceIdAndMetricNameAndUnitAndActiveTrue(Long serviceId, String metricName, String unit);

    boolean existsByServiceIdAndRuleNameIgnoreCase(Long serviceId, String ruleName);

    boolean existsByServiceIdAndRuleNameIgnoreCaseAndIdNot(Long serviceId, String ruleName, Long id);
}
