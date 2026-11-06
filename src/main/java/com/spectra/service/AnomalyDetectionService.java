package com.spectra.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.dto.DetectionResult;
import com.spectra.entity.AnomalyEvent;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.ServiceMetric;
import com.spectra.entity.User;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.AnomalyEventRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.ServiceMetricRepository;
import com.spectra.repository.UserRepository;

@Service
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);

    private final AnomalyEventRepository anomalyEventRepository;
    private final ServiceMetricRepository metricRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final StatisticalAnomalyDetector detector;
    private final UserRepository userRepository;

    @Autowired
    public AnomalyDetectionService(
            AnomalyEventRepository anomalyEventRepository,
            ServiceMetricRepository metricRepository,
            MonitoredServiceRepository serviceRepository,
            StatisticalAnomalyDetector detector,
            UserRepository userRepository) {
        this.anomalyEventRepository = anomalyEventRepository;
        this.metricRepository = metricRepository;
        this.serviceRepository = serviceRepository;
        this.detector = detector;
        this.userRepository = userRepository;
    }

    public AnomalyDetectionService(
            AnomalyEventRepository anomalyEventRepository,
            ServiceMetricRepository metricRepository,
            MonitoredServiceRepository serviceRepository,
            StatisticalAnomalyDetector detector) {
        this(anomalyEventRepository, metricRepository, serviceRepository, detector, null);
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AnomalyEvent> detectAnomaly(ServiceMetric metric) {
        if (metric == null || metric.getService() == null || metric.getMetricName() == null || metric.getMetricValue() == null) {
            return Optional.empty();
        }

        // Idempotency check: avoid creating duplicate anomaly events if invoked twice for the same metric
        if (metric.getId() != null && anomalyEventRepository.existsByMetricId(metric.getId())) {
            return Optional.empty();
        }

        Long serviceId = metric.getService().getId();
        String metricName = metric.getMetricName();
        String unit = metric.getUnit();
        Instant recordedAt = metric.getRecordedAt();

        // Retrieve historical baseline observations strictly occurring before the current metric matching service, metric name, and unit
        List<ServiceMetric> baselineMetrics;
        if (metric.getId() != null) {
            baselineMetrics = metricRepository.findHistoricalBaselineBeforeId(
                    serviceId,
                    metricName,
                    unit,
                    recordedAt,
                    metric.getId(),
                    PageRequest.of(0, StatisticalAnomalyDetector.WINDOW_SIZE)
            );
        } else {
            baselineMetrics = metricRepository.findHistoricalBaselineBeforeTimestamp(
                    serviceId,
                    metricName,
                    unit,
                    recordedAt,
                    PageRequest.of(0, StatisticalAnomalyDetector.WINDOW_SIZE)
            );
        }

        List<Double> historicalValues = baselineMetrics.stream()
                .filter(m -> metric.getId() == null || !metric.getId().equals(m.getId()))
                .map(ServiceMetric::getMetricValue)
                .toList();

        DetectionResult result = detector.detect(metric.getMetricName(), metric.getMetricValue(), metric.getUnit(), historicalValues);

        if (!result.isAnomaly()) {
            return Optional.empty();
        }

        AnomalyEvent event = new AnomalyEvent();
        event.setService(metric.getService());
        event.setMetric(metric);
        event.setMetricName(metric.getMetricName());
        event.setUnit(metric.getUnit());
        event.setObservedValue(metric.getMetricValue());
        event.setBaselineMean(result.baselineMean());
        event.setBaselineStdDev(result.baselineStdDev());
        event.setAnomalyScore(result.anomalyScore());
        event.setSeverity(result.severity());
        event.setDirection(result.direction());
        event.setStatus("DETECTED");
        event.setMessage(result.message());
        event.setDetectedAt(metric.getRecordedAt());

        try {
            AnomalyEvent savedEvent = anomalyEventRepository.save(event);
            return Optional.of(savedEvent);
        } catch (DataIntegrityViolationException ex) {
            if (isMetricIdUniqueConstraintViolation(ex, metric.getId())) {
                log.warn("Concurrent duplicate anomaly event suppressed for metric ID: {}", metric.getId());
                return Optional.empty();
            }
            // Rethrow any unrelated constraint violation (e.g. NOT NULL, length, foreign key failure)
            throw ex;
        }
    }

    private boolean isMetricIdUniqueConstraintViolation(DataIntegrityViolationException ex, Long metricId) {
        if (metricId != null && anomalyEventRepository.existsByMetricId(metricId)) {
            return true;
        }
        String msg = ex.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("uk_anomaly_events_metric_id") || lower.contains("metric_id")) {
                return true;
            }
        }
        Throwable cause = ex.getCause();
        while (cause != null) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null) {
                String lower = causeMsg.toLowerCase();
                if (lower.contains("uk_anomaly_events_metric_id") || lower.contains("metric_id")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Transactional(readOnly = true)
    public List<AnomalyEvent> getRecentAnomalies(int limit) {
        validateQueryLimit(limit);
        User currentUser = getCurrentUser();
        PageRequest pageRequest = PageRequest.of(0, limit);

        if (currentUser != null) {
            if (isAdmin(currentUser)) {
                return anomalyEventRepository.findAllByOrderByDetectedAtDescIdDesc(pageRequest);
            }
            return anomalyEventRepository.findByServiceUserIdOrderByDetectedAtDescIdDesc(currentUser.getId(), pageRequest);
        }
        return anomalyEventRepository.findAllByOrderByDetectedAtDescIdDesc(pageRequest);
    }

    @Transactional(readOnly = true)
    public List<AnomalyEvent> getAnomaliesByServiceId(Long serviceId, int limit) {
        validateQueryLimit(limit);
        User currentUser = getCurrentUser();
        PageRequest pageRequest = PageRequest.of(0, limit);

        if (currentUser != null) {
            if (isAdmin(currentUser)) {
                if (!serviceRepository.existsById(serviceId)) {
                    throw new ResourceNotFoundException("Service not found with id: " + serviceId);
                }
                return anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(serviceId, pageRequest);
            }
            if (!serviceRepository.existsByIdAndUserId(serviceId, currentUser.getId())) {
                throw new ResourceNotFoundException("Service not found with id: " + serviceId);
            }
            return anomalyEventRepository.findByServiceIdAndServiceUserIdOrderByDetectedAtDescIdDesc(serviceId, currentUser.getId(), pageRequest);
        }

        if (!serviceRepository.existsById(serviceId)) {
            throw new ResourceNotFoundException("Service not found with id: " + serviceId);
        }
        return anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(serviceId, pageRequest);
    }

    @Transactional(readOnly = true)
    public AnomalyEvent getAnomalyById(Long id) {
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            if (isAdmin(currentUser)) {
                return anomalyEventRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Anomaly event not found with id: " + id));
            }
            return anomalyEventRepository.findByIdAndServiceUserId(id, currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Anomaly event not found with id: " + id));
        }
        return anomalyEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Anomaly event not found with id: " + id));
    }

    @Transactional
    public AnomalyEvent acknowledgeAnomaly(Long id) {
        User currentUser = getCurrentUser();
        AnomalyEvent anomaly = getAnomalyById(id);

        if ("ACKNOWLEDGED".equals(anomaly.getStatus())) {
            return anomaly;
        }

        anomaly.setStatus("ACKNOWLEDGED");
        anomaly.setAcknowledgedAt(Instant.now());
        anomaly.setAcknowledgedBy(currentUser != null ? currentUser.getEmail() : "SYSTEM");

        return anomalyEventRepository.save(anomaly);
    }

    private void validateQueryLimit(int limit) {
        if (limit <= 0 || limit > 100) {
            throw new BadRequestException("Query limit must be between 1 and 100");
        }
    }
}
