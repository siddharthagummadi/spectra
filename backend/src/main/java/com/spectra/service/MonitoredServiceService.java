package com.spectra.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.dto.CreateServiceRequest;
import com.spectra.dto.UpdateServiceRequest;
import com.spectra.entity.Enterprise;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.AlertEventRepository;
import com.spectra.repository.AlertRuleRepository;
import com.spectra.repository.AnomalyEventRepository;
import com.spectra.repository.IncidentNoteRepository;
import com.spectra.repository.IncidentRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.ServiceMetricRepository;
import com.spectra.repository.UserRepository;

@Service
public class MonitoredServiceService {

    private final MonitoredServiceRepository repository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final IncidentRepository incidentRepository;
    private final IncidentNoteRepository incidentNoteRepository;
    private final AlertRuleRepository alertRuleRepository;
    private final AlertEventRepository alertEventRepository;
    private final AnomalyEventRepository anomalyEventRepository;
    private final ServiceMetricRepository metricRepository;
    private final TenantContextService tenantContextService;

    @Autowired
    public MonitoredServiceService(
            MonitoredServiceRepository repository,
            UserRepository userRepository,
            @Autowired(required = false) AuditLogService auditLogService,
            @Autowired(required = false) IncidentRepository incidentRepository,
            @Autowired(required = false) IncidentNoteRepository incidentNoteRepository,
            @Autowired(required = false) AlertRuleRepository alertRuleRepository,
            @Autowired(required = false) AlertEventRepository alertEventRepository,
            @Autowired(required = false) AnomalyEventRepository anomalyEventRepository,
            @Autowired(required = false) ServiceMetricRepository metricRepository,
            @Autowired(required = false) TenantContextService tenantContextService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.incidentRepository = incidentRepository;
        this.incidentNoteRepository = incidentNoteRepository;
        this.alertRuleRepository = alertRuleRepository;
        this.alertEventRepository = alertEventRepository;
        this.anomalyEventRepository = anomalyEventRepository;
        this.metricRepository = metricRepository;
        this.tenantContextService = tenantContextService;
    }

    public MonitoredServiceService(
            MonitoredServiceRepository repository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            IncidentRepository incidentRepository,
            IncidentNoteRepository incidentNoteRepository,
            AlertRuleRepository alertRuleRepository,
            AlertEventRepository alertEventRepository,
            AnomalyEventRepository anomalyEventRepository,
            ServiceMetricRepository metricRepository) {
        this(repository, userRepository, auditLogService, incidentRepository, incidentNoteRepository,
                alertRuleRepository, alertEventRepository, anomalyEventRepository, metricRepository, null);
    }

    public MonitoredServiceService(MonitoredServiceRepository repository, UserRepository userRepository) {
        this(repository, userRepository, null, null, null, null, null, null, null, null);
    }

    public MonitoredServiceService(MonitoredServiceRepository repository) {
        this(repository, null, null, null, null, null, null, null, null, null);
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
    public MonitoredService createService(CreateServiceRequest request) {
        assertOperationalWriteAllowed();
        if (request == null) {
            throw new BadRequestException("Service creation request cannot be null");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new BadRequestException("Service name is required");
        }
        String normalizedName = request.getName().trim();
        if (normalizedName.length() > 100) {
            throw new BadRequestException("Service name must not exceed 100 characters");
        }
        if (request.getEndpointUrl() == null || !request.getEndpointUrl().trim().matches("^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$")) {
            throw new BadRequestException("Endpoint URL must be a valid HTTP or HTTPS URL");
        }
        if (request.getEnvironment() == null || !request.getEnvironment().trim().matches("^(?i)(DEVELOPMENT|STAGING|PRODUCTION)$")) {
            throw new BadRequestException("Environment must be DEVELOPMENT, STAGING, or PRODUCTION");
        }

        User currentUser = getCurrentUser();
        Enterprise enterprise = resolveEnterprise(currentUser);

        if (enterprise != null) {
            if (repository.existsByEnterpriseIdAndNameIgnoreCase(enterprise.getId(), normalizedName)) {
                throw new IllegalArgumentException("Service with name '" + normalizedName + "' already exists");
            }
        } else if (currentUser != null) {
            if (repository.existsByUserIdAndNameIgnoreCase(currentUser.getId(), normalizedName)) {
                throw new IllegalArgumentException("Service with name '" + normalizedName + "' already exists");
            }
        } else if (repository.existsByNameIgnoreCase(normalizedName)) {
            throw new IllegalArgumentException("Service with name '" + normalizedName + "' already exists");
        }

        MonitoredService service = new MonitoredService();
        service.setName(normalizedName);
        service.setServiceKey(normalizedName.toLowerCase().replaceAll("[^a-z0-9]+", "-"));
        service.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        service.setEndpointUrl(request.getEndpointUrl().trim());
        service.setEnvironment(request.getEnvironment().trim().toUpperCase());
        service.setActive(true);
        if (currentUser != null) {
            service.setUser(currentUser);
        }
        if (enterprise != null) {
            service.setEnterprise(enterprise);
        }

        MonitoredService saved = repository.save(service);
        if (auditLogService != null) {
            auditLogService.recordAction(
                    "SERVICE_CREATED",
                    currentUser != null ? currentUser.getEmail() : "SYSTEM",
                    "Created service: " + saved.getName() + " (" + saved.getEnvironment() + ")"
            );
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<MonitoredService> getAllServices() {
        User currentUser = getCurrentUser();
        if (isAdmin(currentUser)) {
            return repository.findAllWithDetailsOrderByCreatedAtDesc();
        }
        if (currentUser != null) {
            Enterprise enterprise = resolveEnterprise(currentUser);
            if (enterprise != null) {
                return repository.findByEnterpriseIdWithDetailsOrderByCreatedAtDesc(enterprise.getId());
            }
            return repository.findByUserIdWithUserOrderByCreatedAtDesc(currentUser.getId());
        }
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public MonitoredService getServiceById(Long id) {
        User currentUser = getCurrentUser();
        if (isAdmin(currentUser)) {
            return repository.findByIdWithUser(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
        }
        if (currentUser != null) {
            Enterprise enterprise = resolveEnterprise(currentUser);
            if (enterprise != null) {
                return repository.findByIdAndEnterpriseId(id, enterprise.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
            }
            return repository.findByIdAndUserIdWithUser(id, currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
        }
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
    }

    @Transactional
    public MonitoredService updateService(Long id, UpdateServiceRequest request) {
        assertOperationalWriteAllowed();
        if (request == null) {
            throw new BadRequestException("Update service request cannot be null");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new BadRequestException("Service name is required");
        }
        String normalizedName = request.getName().trim();
        if (normalizedName.length() > 100) {
            throw new BadRequestException("Service name must not exceed 100 characters");
        }
        if (request.getEndpointUrl() == null || !request.getEndpointUrl().trim().matches("^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$")) {
            throw new BadRequestException("Endpoint URL must be a valid HTTP or HTTPS URL");
        }
        if (request.getEnvironment() == null || !request.getEnvironment().trim().matches("^(?i)(DEVELOPMENT|STAGING|PRODUCTION)$")) {
            throw new BadRequestException("Environment must be DEVELOPMENT, STAGING, or PRODUCTION");
        }
        User currentUser = getCurrentUser();
        Enterprise enterprise = resolveEnterprise(currentUser);
        MonitoredService existingService;

        if (enterprise != null) {
            existingService = repository.findByIdAndEnterpriseId(id, enterprise.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
            if (repository.existsByEnterpriseIdAndNameIgnoreCaseAndIdNot(enterprise.getId(), normalizedName, id)) {
                throw new IllegalArgumentException("Service with name '" + normalizedName + "' already exists");
            }
        } else if (currentUser != null) {
            existingService = repository.findByIdAndUserId(id, currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
            if (repository.existsByUserIdAndNameIgnoreCaseAndIdNot(currentUser.getId(), normalizedName, id)) {
                throw new IllegalArgumentException("Service with name '" + normalizedName + "' already exists");
            }
        } else {
            existingService = repository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
            if (repository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
                throw new IllegalArgumentException("Service with name '" + normalizedName + "' already exists");
            }
        }

        existingService.setName(normalizedName);
        existingService.setServiceKey(normalizedName.toLowerCase().replaceAll("[^a-z0-9]+", "-"));
        existingService.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        existingService.setEndpointUrl(request.getEndpointUrl().trim());
        existingService.setEnvironment(request.getEnvironment().trim().toUpperCase());

        MonitoredService saved = repository.save(existingService);
        if (auditLogService != null) {
            auditLogService.recordAction(
                    "SERVICE_UPDATED",
                    currentUser != null ? currentUser.getEmail() : "SYSTEM",
                    "Updated service id=" + id + ", name=" + saved.getName()
            );
        }
        return saved;
    }

    @Transactional
    public MonitoredService updateServiceStatus(Long id, boolean active) {
        assertOperationalWriteAllowed();
        User currentUser = getCurrentUser();
        Enterprise enterprise = resolveEnterprise(currentUser);
        MonitoredService existingService;
        if (enterprise != null) {
            existingService = repository.findByIdAndEnterpriseId(id, enterprise.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
        } else if (currentUser != null) {
            existingService = repository.findByIdAndUserId(id, currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
        } else {
            existingService = repository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
        }
        existingService.setActive(active);
        MonitoredService saved = repository.save(existingService);
        if (auditLogService != null) {
            auditLogService.recordAction(
                    "SERVICE_STATUS_CHANGED",
                    currentUser != null ? currentUser.getEmail() : "SYSTEM",
                    "Changed service id=" + id + " active=" + active
            );
        }
        return saved;
    }

    @Transactional
    public void deleteService(Long id) {
        assertOperationalWriteAllowed();
        User currentUser = getCurrentUser();
        Enterprise enterprise = resolveEnterprise(currentUser);

        MonitoredService service;
        if (enterprise != null) {
            service = repository.findByIdAndEnterpriseId(id, enterprise.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
        } else if (currentUser != null) {
            service = repository.findByIdAndUserId(id, currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
        } else {
            service = repository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
        }

        if (incidentRepository != null) {
            incidentRepository.findByServiceIdOrderByTriggeredAtDescIdDesc(id).forEach(inc -> {
                if (incidentNoteRepository != null) {
                    incidentNoteRepository.deleteAll(incidentNoteRepository.findByIncidentIdOrderByCreatedAtAsc(inc.getId()));
                }
                incidentRepository.delete(inc);
            });
        }
        if (alertEventRepository != null) {
            alertEventRepository.deleteAll(alertEventRepository.findByServiceIdOrderByTriggeredAtDescIdDesc(id));
        }
        if (alertRuleRepository != null) {
            alertRuleRepository.deleteAll(alertRuleRepository.findByServiceId(id));
        }
        if (anomalyEventRepository != null) {
            anomalyEventRepository.deleteAll(anomalyEventRepository.findByServiceIdOrderByDetectedAtDescIdDesc(id, org.springframework.data.domain.Pageable.unpaged()));
        }
        if (metricRepository != null) {
            metricRepository.deleteAll(metricRepository.findByServiceIdOrderByRecordedAtDescIdDesc(id, org.springframework.data.domain.Pageable.unpaged()));
        }

        repository.delete(service);

        if (auditLogService != null) {
            auditLogService.recordAction(
                    "SERVICE_DELETED",
                    currentUser != null ? currentUser.getEmail() : "SYSTEM",
                    "Deleted service id=" + id + ", name=" + service.getName()
            );
        }
    }
}
