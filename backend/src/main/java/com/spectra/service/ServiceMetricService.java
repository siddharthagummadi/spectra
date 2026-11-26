package com.spectra.service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.spectra.dto.CreateMetricRequest;
import com.spectra.entity.Enterprise;
import com.spectra.entity.EnterpriseAgent;
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

    private static final Set<String> MULTIVARIATE_METRICS = Set.of(
            "CPU_USAGE", "MEMORY_USAGE", "RESPONSE_TIME", "ERROR_RATE"
    );

    private static final Set<String> SUPPORTED_METRICS = Set.of(
            "CPU_USAGE",
            "MEMORY_USAGE",
            "DISK_USAGE",
            "NETWORK_USAGE",
            "SYSTEM_UPTIME",
            "RESPONSE_TIME",
            "ERROR_RATE",
            "REQUEST_COUNT",
            "SERVICE_AVAILABILITY",
            "APPLICATION_HEALTH"
    );

    private final ServiceMetricRepository metricRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final AlertService alertService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final UserRepository userRepository;
    private final com.spectra.service.multivariate.MultivariateAnomalyOrchestrator multivariateOrchestrator;
    private final TenantContextService tenantContextService;

    @Autowired
    public ServiceMetricService(
            ServiceMetricRepository metricRepository,
            MonitoredServiceRepository serviceRepository,
            AlertService alertService,
            AnomalyDetectionService anomalyDetectionService,
            UserRepository userRepository,
            @Autowired(required = false) com.spectra.service.multivariate.MultivariateAnomalyOrchestrator multivariateOrchestrator,
            @Autowired(required = false) TenantContextService tenantContextService) {
        this.metricRepository = metricRepository;
        this.serviceRepository = serviceRepository;
        this.alertService = alertService;
        this.anomalyDetectionService = anomalyDetectionService;
        this.userRepository = userRepository;
        this.multivariateOrchestrator = multivariateOrchestrator;
        this.tenantContextService = tenantContextService;
    }

    public ServiceMetricService(
            ServiceMetricRepository metricRepository,
            MonitoredServiceRepository serviceRepository,
            AlertService alertService,
            AnomalyDetectionService anomalyDetectionService,
            UserRepository userRepository,
            com.spectra.service.multivariate.MultivariateAnomalyOrchestrator multivariateOrchestrator) {
        this(metricRepository, serviceRepository, alertService, anomalyDetectionService, userRepository, multivariateOrchestrator, null);
    }

    public ServiceMetricService(
            ServiceMetricRepository metricRepository,
            MonitoredServiceRepository serviceRepository,
            AlertService alertService,
            AnomalyDetectionService anomalyDetectionService,
            UserRepository userRepository) {
        this(metricRepository, serviceRepository, alertService, anomalyDetectionService, userRepository, null, null);
    }

    public ServiceMetricService(
            ServiceMetricRepository metricRepository,
            MonitoredServiceRepository serviceRepository,
            AlertService alertService) {
        this(metricRepository, serviceRepository, alertService, null, null, null, null);
    }

    private User getCurrentUser() {
        if (tenantContextService != null) {
            return tenantContextService.getCurrentUser();
        }
        if (userRepository == null) return null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private boolean isAdmin(User user) {
        if (tenantContextService != null && tenantContextService.isPrincipalAdmin()) {
            return true;
        }
        return user != null && user.getRole() == Role.ADMIN;
    }

    private void assertOperationalWriteAllowed() {
        if (tenantContextService != null) {
            tenantContextService.assertOperationalWriteAllowed();
        } else if (isAdmin(getCurrentUser())) {
            throw new AccessDeniedException("Platform administrators have read-only access to enterprise operational records");
        }
    }

    private Enterprise resolveEnterprise(User user) {
        if (user == null || isAdmin(user)) {
            return null;
        }
        if (tenantContextService != null) {
            return tenantContextService.resolveOrProvisionEnterprise(user);
        }
        return user.getEnterprise();
    }

    @Transactional
    public ServiceMetric submitMetric(CreateMetricRequest request) {
        assertOperationalWriteAllowed();
        if (request == null) {
            throw new BadRequestException("Metric submission request cannot be null");
        }
        if (request.getServiceId() == null) {
            throw new BadRequestException("Service ID is required");
        }

        String normalizedName = normalizeMetricName(request.getMetricName());
        Double value = request.getMetricValue();
        validateNumericValue(value);

        String unit = resolveAndValidateUnit(normalizedName, request.getUnit());
        validateMetricRange(normalizedName, unit, value);

        Instant recordedAt = resolveRecordedAt(request.getRecordedAt(), false);

        User currentUser = getCurrentUser();
        Enterprise enterprise = resolveEnterprise(currentUser);
        MonitoredService service;
        if (enterprise != null) {
            service = serviceRepository.findByIdAndEnterpriseId(request.getServiceId(), enterprise.getId())
                    .or(() -> serviceRepository.findByIdAndUserId(request.getServiceId(), currentUser.getId())
                            .filter(s -> s.getEnterprise() == null))
                    .or(() -> serviceRepository.findById(request.getServiceId()).filter(s -> s.getEnterprise() == null && s.getUser() == null))
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + request.getServiceId()));
        } else if (currentUser != null) {
            service = serviceRepository.findByIdAndUserId(request.getServiceId(), currentUser.getId())
                    .or(() -> serviceRepository.findById(request.getServiceId()).filter(s -> s.getUser() == null))
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + request.getServiceId()));
        } else {
            service = serviceRepository.findById(request.getServiceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + request.getServiceId()));
        }

        return persistAndEvaluateMetric(service, normalizedName, value, unit, recordedAt, service.getEnterprise(), null, "API");
    }

    /**
     * Internal ingestion entry point used by validated Enterprise Monitoring Agents.
     * Bypasses human UserDetails lookup while enforcing agent-to-enterprise service binding.
     */
    @Transactional
    public ServiceMetric submitAgentMetric(
            MonitoredService service,
            EnterpriseAgent agent,
            String rawMetricName,
            Double value,
            String rawUnit,
            Instant rawRecordedAt) {
        if (service == null || agent == null || agent.getEnterprise() == null) {
            throw new BadRequestException("Valid service and authenticated enterprise agent are required");
        }
        if (service.getEnterprise() == null || !service.getEnterprise().getId().equals(agent.getEnterprise().getId())) {
            throw new AccessDeniedException("Agent enterprise does not own target service");
        }

        String normalizedName = normalizeMetricName(rawMetricName);
        validateNumericValue(value);
        String unit = resolveAndValidateUnit(normalizedName, rawUnit);
        validateMetricRange(normalizedName, unit, value);
        Instant recordedAt = resolveRecordedAt(rawRecordedAt, true);

        if (metricRepository.existsByServiceIdAndMetricNameAndUnitAndRecordedAtAndAgentIsNotNull(
                service.getId(), normalizedName, unit, recordedAt)) {
            return null; // Duplicate sample already persisted from prior attempt
        }

        return persistAndEvaluateMetric(service, normalizedName, value, unit, recordedAt, agent.getEnterprise(), agent, "AGENT");
    }

    private ServiceMetric persistAndEvaluateMetric(
            MonitoredService service,
            String normalizedName,
            Double value,
            String unit,
            Instant recordedAt,
            Enterprise enterprise,
            EnterpriseAgent agent,
            String sourceType) {

        ServiceMetric metric = new ServiceMetric();
        metric.setService(service);
        metric.setMetricName(normalizedName);
        metric.setMetricValue(value);
        metric.setUnit(unit);
        metric.setRecordedAt(recordedAt);
        metric.setEnvironment(service.getEnvironment());
        metric.setEnterprise(enterprise != null ? enterprise : service.getEnterprise());
        metric.setAgent(agent);
        metric.setSourceType(sourceType);

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

        if (multivariateOrchestrator != null
                && multivariateOrchestrator.isEnabled()
                && MULTIVARIATE_METRICS.contains(normalizedName)) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            multivariateOrchestrator.processMetric(savedMetric);
                        } catch (Exception ex) {
                            log.error("Multivariate anomaly detection evaluation failed after commit for metric id {}: {}",
                                    savedMetric.getId(), ex.getMessage(), ex);
                        }
                    }
                });
            } else {
                try {
                    multivariateOrchestrator.processMetric(savedMetric);
                } catch (Exception ex) {
                    log.error("Multivariate anomaly detection evaluation failed for metric id {}: {}",
                            savedMetric.getId(), ex.getMessage());
                }
            }
        }
        return savedMetric;
    }

    @Transactional
    public List<ServiceMetric> submitMetrics(List<CreateMetricRequest> requests) {
        assertOperationalWriteAllowed();
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }
        return requests.stream()
                .map(this::submitMetric)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceMetric> getMetricsByServiceId(Long serviceId, int limit) {
        User currentUser = getCurrentUser();
        if (isAdmin(currentUser)) {
            if (!serviceRepository.existsById(serviceId)) {
                throw new ResourceNotFoundException("Service not found with id: " + serviceId);
            }
            validateQueryLimit(limit);
            return metricRepository.findByServiceIdOrderByRecordedAtDescIdDesc(serviceId, PageRequest.of(0, limit));
        }
        if (currentUser != null) {
            Enterprise enterprise = resolveEnterprise(currentUser);
            if (enterprise != null) {
                if (serviceRepository.existsByIdAndEnterpriseId(serviceId, enterprise.getId())) {
                    validateQueryLimit(limit);
                    return metricRepository.findByServiceIdAndServiceEnterpriseIdOrderByRecordedAtDescIdDesc(
                            serviceId, enterprise.getId(), PageRequest.of(0, limit));
                }
                if (serviceRepository.existsByIdAndUserId(serviceId, currentUser.getId())) {
                    validateQueryLimit(limit);
                    return metricRepository.findByServiceIdAndServiceUserIdOrderByRecordedAtDescIdDesc(
                            serviceId, currentUser.getId(), PageRequest.of(0, limit));
                }
                throw new ResourceNotFoundException("Service not found with id: " + serviceId);
            }
            if (!serviceRepository.existsByIdAndUserId(serviceId, currentUser.getId())) {
                throw new ResourceNotFoundException("Service not found with id: " + serviceId);
            }
            validateQueryLimit(limit);
            return metricRepository.findByServiceIdAndServiceUserIdOrderByRecordedAtDescIdDesc(
                    serviceId, currentUser.getId(), PageRequest.of(0, limit));
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
        if (isAdmin(currentUser)) {
            return metricRepository.findAllByOrderByRecordedAtDescIdDesc(PageRequest.of(0, limit));
        }
        if (currentUser != null) {
            Enterprise enterprise = resolveEnterprise(currentUser);
            if (enterprise != null) {
                return metricRepository.findByServiceEnterpriseIdOrderByRecordedAtDescIdDesc(enterprise.getId(), PageRequest.of(0, limit));
            }
            return metricRepository.findByServiceUserIdOrderByRecordedAtDescIdDesc(currentUser.getId(), PageRequest.of(0, limit));
        }
        return metricRepository.findAllByOrderByRecordedAtDescIdDesc(PageRequest.of(0, limit));
    }

    private String normalizeMetricName(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            throw new BadRequestException("Metric name is required");
        }
        String trimmed = rawName.trim().toUpperCase();
        if (!SUPPORTED_METRICS.contains(trimmed)) {
            throw new BadRequestException("Unsupported metric name: " + rawName
                    + ". Supported metrics are CPU_USAGE, MEMORY_USAGE, RESPONSE_TIME, ERROR_RATE");
        }
        return trimmed;
    }

    private void validateNumericValue(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            throw new BadRequestException("Metric value must be a valid finite number");
        }
        if (value < 0.0) {
            throw new BadRequestException("Metric value cannot be negative");
        }
    }

    private String resolveAndValidateUnit(String metricName, String rawUnit) {
        String trimmedUnit = rawUnit != null ? rawUnit.trim().toUpperCase() : null;

        if (metricName.equals("CPU_USAGE") || metricName.equals("ERROR_RATE")
                || metricName.equals("DISK_USAGE") || metricName.equals("SERVICE_AVAILABILITY")
                || metricName.equals("APPLICATION_HEALTH")) {
            if (trimmedUnit == null || trimmedUnit.isEmpty()) {
                return "PERCENT";
            }
            if (!trimmedUnit.equals("PERCENT") && !trimmedUnit.equals("STATUS")) {
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

        if (metricName.equals("NETWORK_USAGE")) {
            if (trimmedUnit == null || trimmedUnit.isEmpty()) {
                return "KBPS";
            }
            if (!trimmedUnit.equals("KBPS") && !trimmedUnit.equals("MBPS") && !trimmedUnit.equals("BYTES")) {
                throw new BadRequestException("NETWORK_USAGE unit must be KBPS, MBPS, or BYTES");
            }
            return trimmedUnit;
        }

        if (metricName.equals("SYSTEM_UPTIME")) {
            if (trimmedUnit == null || trimmedUnit.isEmpty()) {
                return "SECONDS";
            }
            if (!trimmedUnit.equals("SECONDS")) {
                throw new BadRequestException("SYSTEM_UPTIME unit must be SECONDS");
            }
            return trimmedUnit;
        }

        if (metricName.equals("REQUEST_COUNT")) {
            if (trimmedUnit == null || trimmedUnit.isEmpty()) {
                return "COUNT";
            }
            if (!trimmedUnit.equals("COUNT")) {
                throw new BadRequestException("REQUEST_COUNT unit must be COUNT");
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
        if (unit.equals("MILLISECONDS") && value > 3_600_000.0) {
            throw new BadRequestException("Response time value for " + metricName + " exceeds maximum threshold (3,600,000 ms)");
        }
        if (unit.equals("MEGABYTES") && value > 100_000_000.0) {
            throw new BadRequestException("Memory usage value for " + metricName + " exceeds maximum threshold (100,000,000 MB)");
        }
    }

    private Instant resolveRecordedAt(Instant recordedAt, boolean allowAgentClockSkew) {
        if (recordedAt == null) {
            return Instant.now();
        }
        Instant maxAllowed = allowAgentClockSkew ? Instant.now().plusSeconds(60) : Instant.now();
        if (recordedAt.isAfter(maxAllowed)) {
            throw new BadRequestException("Measurement timestamp cannot be in the future");
        }
        if (recordedAt.isBefore(Instant.now().minus(java.time.Duration.ofDays(30)))) {
            throw new BadRequestException("Measurement timestamp cannot be older than 30 days in the past");
        }
        return recordedAt;
    }

    private void validateQueryLimit(int limit) {
        if (limit <= 0 || limit > 100) {
            throw new BadRequestException("Query limit must be between 1 and 100");
        }
    }
}
