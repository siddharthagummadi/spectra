package com.spectra.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.spectra.dto.CreateMetricRequest;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.ServiceMetric;
import com.spectra.entity.User;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.ServiceMetricRepository;
import com.spectra.repository.UserRepository;

@Service
public class ServiceMetricService {

    private static final Logger log = LoggerFactory.getLogger(ServiceMetricService.class);

    private final ServiceMetricRepository metricRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final AlertService alertService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final UserRepository userRepository;

    @Autowired
    public ServiceMetricService(
            ServiceMetricRepository metricRepository,
            MonitoredServiceRepository serviceRepository,
            AlertService alertService,
            AnomalyDetectionService anomalyDetectionService,
            UserRepository userRepository) {
        this.metricRepository = metricRepository;
        this.serviceRepository = serviceRepository;
        this.alertService = alertService;
        this.anomalyDetectionService = anomalyDetectionService;
        this.userRepository = userRepository;
    }

    public ServiceMetricService(
            ServiceMetricRepository metricRepository,
            MonitoredServiceRepository serviceRepository,
            AlertService alertService) {
        this(metricRepository, serviceRepository, alertService, null, null);
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

    @Transactional
    public ServiceMetric submitMetric(CreateMetricRequest request) {
        User currentUser = getCurrentUser();
        MonitoredService service;
        if (currentUser != null) {
            service = serviceRepository.findByIdAndUserId(request.getServiceId(), currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + request.getServiceId()));
        } else {
            service = serviceRepository.findById(request.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + request.getServiceId()));
        }

        String normalizedName = normalizeMetricName(request.getMetricName());
        Double value = request.getMetricValue();
        validateNumericValue(value);

        String unit = resolveAndValidateUnit(normalizedName, request.getUnit());
        validateMetricRange(normalizedName, unit, value);

        Instant recordedAt = resolveRecordedAt(request.getRecordedAt());

        ServiceMetric metric = new ServiceMetric();
        metric.setService(service);
        metric.setMetricName(normalizedName);
        metric.setMetricValue(value);
        metric.setUnit(unit);
        metric.setRecordedAt(recordedAt);

        ServiceMetric savedMetric = metricRepository.save(metric);
        alertService.evaluateMetric(savedMetric);

        if (anomalyDetectionService != null) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            anomalyDetectionService.detectAnomaly(savedMetric);
                        } catch (Exception ex) {
                            log.error("Anomaly detection evaluation failed after commit for metric id {}: {}",
                                    savedMetric.getId(), ex.getMessage(), ex);
                        }
                    }
                });
            } else {
                try {
                    anomalyDetectionService.detectAnomaly(savedMetric);
                } catch (Exception ex) {
                    log.error("Anomaly detection evaluation failed for metric id {}: {}",
                            savedMetric.getId(), ex.getMessage());
                }
            }
        }
        return savedMetric;
    }

    @Transactional(readOnly = true)
    public List<ServiceMetric> getMetricsByServiceId(Long serviceId, int limit) {
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            if (isAdmin(currentUser)) {
                if (!serviceRepository.existsById(serviceId)) {
                    throw new ResourceNotFoundException("Service not found with id: " + serviceId);
                }
                validateQueryLimit(limit);
                return metricRepository.findByServiceIdOrderByRecordedAtDescIdDesc(serviceId, PageRequest.of(0, limit));
            }
            if (!serviceRepository.existsByIdAndUserId(serviceId, currentUser.getId())) {
                throw new ResourceNotFoundException("Service not found with id: " + serviceId);
            }
            validateQueryLimit(limit);
            return metricRepository.findByServiceIdAndServiceUserIdOrderByRecordedAtDescIdDesc(serviceId, currentUser.getId(), PageRequest.of(0, limit));
        }
        if (!serviceRepository.existsById(serviceId)) {
            throw new ResourceNotFoundException("Service not found with id: " + serviceId);
        }
        validateQueryLimit(limit);
        return metricRepository.findByServiceIdOrderByRecordedAtDescIdDesc(serviceId, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<ServiceMetric> getRecentMetrics(int limit) {
        validateQueryLimit(limit);
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            if (isAdmin(currentUser)) {
                return metricRepository.findAllByOrderByRecordedAtDescIdDesc(PageRequest.of(0, limit));
            }
            return metricRepository.findByServiceUserIdOrderByRecordedAtDescIdDesc(currentUser.getId(), PageRequest.of(0, limit));
        }
        return metricRepository.findAllByOrderByRecordedAtDescIdDesc(PageRequest.of(0, limit));
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

    private void validateNumericValue(Double value) {
        if (value.isNaN() || value.isInfinite()) {
            throw new BadRequestException("Metric value must be a valid finite number");
        }
        if (value < 0.0) {
            throw new BadRequestException("Metric value cannot be negative");
        }
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

    private void validateMetricRange(String metricName, String unit, Double value) {
        if (unit.equals("PERCENT") && value > 100.0) {
            throw new BadRequestException("Percentage metric value for " + metricName + " must be between 0.0 and 100.0");
        }
    }

    private Instant resolveRecordedAt(Instant recordedAt) {
        if (recordedAt == null) {
            return Instant.now();
        }
        if (recordedAt.isAfter(Instant.now())) {
            throw new BadRequestException("Measurement timestamp cannot be in the future");
        }
        return recordedAt;
    }

    private void validateQueryLimit(int limit) {
        if (limit <= 0 || limit > 100) {
            throw new BadRequestException("Query limit must be between 1 and 100");
        }
    }
}
