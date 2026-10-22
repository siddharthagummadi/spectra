package com.spectra.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.dto.CreateAlertRuleRequest;
import com.spectra.dto.UpdateAlertRuleRequest;
import com.spectra.entity.AlertEvent;
import com.spectra.entity.AlertRule;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.ServiceMetric;
import com.spectra.entity.User;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.AlertEventRepository;
import com.spectra.repository.AlertRuleRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.UserRepository;

@Service
public class AlertService {

    private final AlertRuleRepository ruleRepository;
    private final AlertEventRepository alertEventRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final UserRepository userRepository;

    @Autowired
    public AlertService(
            AlertRuleRepository ruleRepository,
            AlertEventRepository alertEventRepository,
            MonitoredServiceRepository serviceRepository,
            UserRepository userRepository) {
        this.ruleRepository = ruleRepository;
        this.alertEventRepository = alertEventRepository;
        this.serviceRepository = serviceRepository;
        this.userRepository = userRepository;
    }

    public AlertService(
            AlertRuleRepository ruleRepository,
            AlertEventRepository alertEventRepository,
            MonitoredServiceRepository serviceRepository) {
        this(ruleRepository, alertEventRepository, serviceRepository, null);
    }

    private User getCurrentUser() {
        if (userRepository == null) return null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    private AlertRule getOwnedRuleById(Long id, User currentUser) {
        if (currentUser != null) {
            return ruleRepository.findByIdAndServiceUserId(id, currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Alert rule not found with id: " + id));
        }
        return ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert rule not found with id: " + id));
    }

    @Transactional
    public AlertRule createAlertRule(CreateAlertRuleRequest request) {
        User currentUser = getCurrentUser();
        MonitoredService service;
        if (currentUser != null) {
            service = serviceRepository.findByIdAndUserId(request.getServiceId(), currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + request.getServiceId()));
        } else {
            service = serviceRepository.findById(request.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + request.getServiceId()));
        }

        String ruleName = request.getRuleName().trim();
        if (ruleRepository.existsByServiceIdAndRuleNameIgnoreCase(service.getId(), ruleName)) {
            throw new IllegalArgumentException("Alert rule with name '" + ruleName + "' already exists for this service");
        }

        String metricName = normalizeMetricName(request.getMetricName());
        String unit = resolveAndValidateUnit(metricName, request.getUnit());
        String comparator = validateComparator(request.getComparator());
        String severity = validateSeverity(request.getSeverity());
        validateThresholdValue(metricName, unit, request.getThresholdValue());

        AlertRule rule = new AlertRule();
        rule.setService(service);
        rule.setRuleName(ruleName);
        rule.setMetricName(metricName);
        rule.setUnit(unit);
        rule.setComparator(comparator);
        rule.setThresholdValue(request.getThresholdValue());
        rule.setSeverity(severity);
        rule.setActive(true);

        return ruleRepository.save(rule);
    }

    @Transactional
    public AlertRule updateAlertRule(Long id, UpdateAlertRuleRequest request) {
        User currentUser = getCurrentUser();
        AlertRule rule = getOwnedRuleById(id, currentUser);

        String ruleName = request.getRuleName().trim();
        if (ruleRepository.existsByServiceIdAndRuleNameIgnoreCaseAndIdNot(rule.getService().getId(), ruleName, id)) {
            throw new IllegalArgumentException("Alert rule with name '" + ruleName + "' already exists for this service");
        }

        String metricName = normalizeMetricName(request.getMetricName());
        String unit = resolveAndValidateUnit(metricName, request.getUnit());
        String comparator = validateComparator(request.getComparator());
        String severity = validateSeverity(request.getSeverity());
        validateThresholdValue(metricName, unit, request.getThresholdValue());

        boolean ruleCriteriaChanged = !rule.getThresholdValue().equals(request.getThresholdValue())
                || !rule.getComparator().equals(comparator)
                || !rule.getMetricName().equals(metricName)
                || !rule.getUnit().equals(unit);

        if (ruleCriteriaChanged) {
            resolveActiveAlertsForRule(id, Instant.now(), null, "Alert resolved due to rule reconfiguration");
        }

        rule.setRuleName(ruleName);
        rule.setMetricName(metricName);
        rule.setUnit(unit);
        rule.setComparator(comparator);
        rule.setThresholdValue(request.getThresholdValue());
        rule.setSeverity(severity);

        return ruleRepository.save(rule);
    }

    @Transactional
    public AlertRule updateAlertRuleStatus(Long id, boolean active) {
        User currentUser = getCurrentUser();
        AlertRule rule = getOwnedRuleById(id, currentUser);

        if (!active && rule.isActive()) {
            resolveActiveAlertsForRule(id, Instant.now(), null, "Alert resolved because rule was deactivated");
        }

        rule.setActive(active);
        return ruleRepository.save(rule);
    }

    @Transactional
    public void deleteAlertRule(Long id) {
        User currentUser = getCurrentUser();
        AlertRule rule = getOwnedRuleById(id, currentUser);
        List<AlertEvent> events = alertEventRepository.findByRuleIdAndStatusIn(id, List.of("TRIGGERED", "ACKNOWLEDGED", "RESOLVED"));
        if (!events.isEmpty()) {
            throw new IllegalArgumentException("Cannot delete alert rule with associated alert events. Deactivate the rule instead.");
        }
        ruleRepository.delete(rule);
    }

    @Transactional(readOnly = true)
    public AlertRule getRuleById(Long id) {
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            if (isAdmin(currentUser)) {
                return ruleRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Alert rule not found with id: " + id));
            }
            return ruleRepository.findByIdAndServiceUserId(id, currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Alert rule not found with id: " + id));
        }
        return ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert rule not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<AlertRule> getAllRules() {
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            if (isAdmin(currentUser)) {
                return ruleRepository.findAll();
            }
            return ruleRepository.findByServiceUserId(currentUser.getId());
        }
        return ruleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<AlertRule> getRulesByServiceId(Long serviceId) {
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            if (isAdmin(currentUser)) {
                if (!serviceRepository.existsById(serviceId)) {
                    throw new ResourceNotFoundException("Service not found with id: " + serviceId);
                }
                return ruleRepository.findByServiceId(serviceId);
            }
            if (!serviceRepository.existsByIdAndUserId(serviceId, currentUser.getId())) {
                throw new ResourceNotFoundException("Service not found with id: " + serviceId);
            }
            return ruleRepository.findByServiceIdAndServiceUserId(serviceId, currentUser.getId());
        }
        if (!serviceRepository.existsById(serviceId)) {
            throw new ResourceNotFoundException("Service not found with id: " + serviceId);
        }
        return ruleRepository.findByServiceId(serviceId);
    }

    @Transactional
    public void evaluateMetric(ServiceMetric metric) {
        List<AlertRule> rules = ruleRepository.findByServiceIdAndMetricNameAndUnitAndActiveTrue(
                metric.getService().getId(),
                metric.getMetricName(),
                metric.getUnit()
        );

        for (AlertRule rule : rules) {
            boolean breached = isBreached(metric.getMetricValue(), rule.getComparator(), rule.getThresholdValue());

            if (breached) {
                Optional<AlertEvent> activeAlert = alertEventRepository.findFirstByRuleIdAndStatusIn(
                        rule.getId(),
                        List.of("TRIGGERED", "ACKNOWLEDGED")
                );

                if (activeAlert.isEmpty()) {
                    AlertEvent alert = new AlertEvent();
                    alert.setRule(rule);
                    alert.setService(metric.getService());
                    alert.setMetricName(metric.getMetricName());
                    alert.setUnit(metric.getUnit());
                    alert.setTriggerValue(metric.getMetricValue());
                    alert.setThresholdValue(rule.getThresholdValue());
                    alert.setSeverity(rule.getSeverity());
                    alert.setStatus("TRIGGERED");
                    alert.setMessage(metric.getMetricName() + " (" + metric.getMetricValue() + " " + metric.getUnit()
                            + ") breached rule '" + rule.getRuleName() + "'");
                    alert.setTriggeredAt(metric.getRecordedAt());

                    alertEventRepository.save(alert);
                }
            } else {
                Optional<AlertEvent> activeAlert = alertEventRepository.findFirstByRuleIdAndStatusIn(
                        rule.getId(),
                        List.of("TRIGGERED", "ACKNOWLEDGED")
                );

                if (activeAlert.isPresent()) {
                    AlertEvent alert = activeAlert.get();
                    alert.setStatus("RESOLVED");
                    alert.setResolvedAt(metric.getRecordedAt());
                    alert.setResolvedValue(metric.getMetricValue());

                    alertEventRepository.save(alert);
                }
            }
        }
    }

    @Transactional
    public AlertEvent acknowledgeAlert(Long alertId) {
        User currentUser = getCurrentUser();
        AlertEvent alert;
        if (currentUser != null) {
            alert = alertEventRepository.findByIdAndServiceUserId(alertId, currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + alertId));
        } else {
            alert = alertEventRepository.findById(alertId)
                    .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + alertId));
        }

        if (alert.getStatus().equals("RESOLVED")) {
            throw new BadRequestException("Cannot acknowledge an alert that has already been resolved");
        }

        if (alert.getStatus().equals("TRIGGERED")) {
            alert.setStatus("ACKNOWLEDGED");
            return alertEventRepository.save(alert);
        }

        return alert;
    }

    @Transactional(readOnly = true)
    public List<AlertEvent> getAllAlerts(String status) {
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            if (isAdmin(currentUser)) {
                if (status != null && !status.trim().isEmpty()) {
                    return alertEventRepository.findByStatusOrderByTriggeredAtDescIdDesc(status.trim().toUpperCase());
                }
                return alertEventRepository.findAllByOrderByTriggeredAtDescIdDesc();
            }
            if (status != null && !status.trim().isEmpty()) {
                return alertEventRepository.findByServiceUserIdAndStatusOrderByTriggeredAtDescIdDesc(currentUser.getId(), status.trim().toUpperCase());
            }
            return alertEventRepository.findByServiceUserIdOrderByTriggeredAtDescIdDesc(currentUser.getId());
        }
        if (status != null && !status.trim().isEmpty()) {
            return alertEventRepository.findByStatusOrderByTriggeredAtDescIdDesc(status.trim().toUpperCase());
        }
        return alertEventRepository.findAllByOrderByTriggeredAtDescIdDesc();
    }

    @Transactional(readOnly = true)
    public List<AlertEvent> getAlertsByServiceId(Long serviceId, String status) {
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            if (isAdmin(currentUser)) {
                if (!serviceRepository.existsById(serviceId)) {
                    throw new ResourceNotFoundException("Service not found with id: " + serviceId);
                }
                if (status != null && !status.trim().isEmpty()) {
                    return alertEventRepository.findByServiceIdAndStatusOrderByTriggeredAtDescIdDesc(serviceId, status.trim().toUpperCase());
                }
                return alertEventRepository.findByServiceIdOrderByTriggeredAtDescIdDesc(serviceId);
            }
            if (!serviceRepository.existsByIdAndUserId(serviceId, currentUser.getId())) {
                throw new ResourceNotFoundException("Service not found with id: " + serviceId);
            }
            if (status != null && !status.trim().isEmpty()) {
                return alertEventRepository.findByServiceIdAndServiceUserIdAndStatusOrderByTriggeredAtDescIdDesc(serviceId, currentUser.getId(), status.trim().toUpperCase());
            }
            return alertEventRepository.findByServiceIdAndServiceUserIdOrderByTriggeredAtDescIdDesc(serviceId, currentUser.getId());
        }
        if (!serviceRepository.existsById(serviceId)) {
            throw new ResourceNotFoundException("Service not found with id: " + serviceId);
        }
        if (status != null && !status.trim().isEmpty()) {
            return alertEventRepository.findByServiceIdAndStatusOrderByTriggeredAtDescIdDesc(serviceId, status.trim().toUpperCase());
        }
        return alertEventRepository.findByServiceIdOrderByTriggeredAtDescIdDesc(serviceId);
    }

    @Transactional(readOnly = true)
    public AlertEvent getAlertById(Long id) {
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            if (isAdmin(currentUser)) {
                return alertEventRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + id));
            }
            return alertEventRepository.findByIdAndServiceUserId(id, currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + id));
        }
        return alertEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found with id: " + id));
    }

    private void resolveActiveAlertsForRule(Long ruleId, Instant resolvedAt, Double resolvedValue, String note) {
        List<AlertEvent> openAlerts = alertEventRepository.findByRuleIdAndStatusIn(
                ruleId,
                List.of("TRIGGERED", "ACKNOWLEDGED")
        );

        for (AlertEvent alert : openAlerts) {
            alert.setStatus("RESOLVED");
            alert.setResolvedAt(resolvedAt);
            alert.setResolvedValue(resolvedValue);
            if (note != null) {
                alert.setMessage(alert.getMessage() + " [" + note + "]");
            }
            alertEventRepository.save(alert);
        }
    }

    private boolean isBreached(Double value, String comparator, Double threshold) {
        return switch (comparator) {
            case "GREATER_THAN" -> value > threshold;
            case "GREATER_THAN_OR_EQUAL" -> value >= threshold;
            case "LESS_THAN" -> value < threshold;
            case "LESS_THAN_OR_EQUAL" -> value <= threshold;
            default -> false;
        };
    }

    private String normalizeMetricName(String rawName) {
        String trimmed = rawName.trim().toUpperCase();
        if (!trimmed.equals("CPU_USAGE")
                && !trimmed.equals("MEMORY_USAGE")
                && !trimmed.equals("RESPONSE_TIME")
                && !trimmed.equals("ERROR_RATE")) {
            throw new BadRequestException("Unsupported metric name: " + rawName
                    + ". Supported metrics are CPU_USAGE, MEMORY_USAGE, RESPONSE_TIME, ERROR_RATE");
        }
        return trimmed;
    }

    private String resolveAndValidateUnit(String metricName, String rawUnit) {
        String trimmedUnit = rawUnit != null ? rawUnit.trim().toUpperCase() : null;

        if (metricName.equals("CPU_USAGE") || metricName.equals("ERROR_RATE")) {
            if (trimmedUnit == null || trimmedUnit.isEmpty()) {
                return "PERCENT";
            }
            if (!trimmedUnit.equals("PERCENT")) {
                throw new BadRequestException(metricName + " unit must be PERCENT");
            }
            return trimmedUnit;
        }

        if (metricName.equals("RESPONSE_TIME")) {
            if (trimmedUnit == null || trimmedUnit.isEmpty()) {
                return "MILLISECONDS";
            }
            if (!trimmedUnit.equals("MILLISECONDS")) {
                throw new BadRequestException("RESPONSE_TIME unit must be MILLISECONDS");
            }
            return trimmedUnit;
        }

        if (trimmedUnit == null || trimmedUnit.isEmpty()) {
            throw new BadRequestException("Unit is required for MEMORY_USAGE (must be PERCENT or MEGABYTES)");
        }
        if (!trimmedUnit.equals("PERCENT") && !trimmedUnit.equals("MEGABYTES")) {
            throw new BadRequestException("MEMORY_USAGE unit must be PERCENT or MEGABYTES");
        }
        return trimmedUnit;
    }

    private String validateComparator(String rawComparator) {
        String trimmed = rawComparator.trim().toUpperCase();
        if (!trimmed.equals("GREATER_THAN")
                && !trimmed.equals("GREATER_THAN_OR_EQUAL")
                && !trimmed.equals("LESS_THAN")
                && !trimmed.equals("LESS_THAN_OR_EQUAL")) {
            throw new BadRequestException("Invalid comparator: " + rawComparator
                    + ". Allowed comparators are GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL");
        }
        return trimmed;
    }

    private String validateSeverity(String rawSeverity) {
        String trimmed = rawSeverity.trim().toUpperCase();
        if (!trimmed.equals("LOW")
                && !trimmed.equals("MEDIUM")
                && !trimmed.equals("HIGH")
                && !trimmed.equals("CRITICAL")) {
            throw new BadRequestException("Invalid severity: " + rawSeverity
                    + ". Allowed severities are LOW, MEDIUM, HIGH, CRITICAL");
        }
        return trimmed;
    }

    private void validateThresholdValue(String metricName, String unit, Double threshold) {
        if (threshold.isNaN() || threshold.isInfinite()) {
            throw new BadRequestException("Threshold value must be a valid finite number");
        }
        if (threshold < 0.0) {
            throw new BadRequestException("Threshold value cannot be negative");
        }
        if (unit.equals("PERCENT") && threshold > 100.0) {
            throw new BadRequestException("Percentage threshold for " + metricName + " must be between 0.0 and 100.0");
        }
    }
}
