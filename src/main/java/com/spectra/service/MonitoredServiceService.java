package com.spectra.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spectra.dto.CreateServiceRequest;
import com.spectra.dto.UpdateServiceRequest;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.User;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.UserRepository;

@Service
public class MonitoredServiceService {

    private final MonitoredServiceRepository repository;
    private final UserRepository userRepository;

    @Autowired
    public MonitoredServiceService(MonitoredServiceRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    public MonitoredServiceService(MonitoredServiceRepository repository) {
        this(repository, null);
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
    public MonitoredService createService(CreateServiceRequest request) {
        String normalizedName = request.getName().trim();
        User currentUser = getCurrentUser();

        if (currentUser != null) {
            if (repository.existsByUserIdAndNameIgnoreCase(currentUser.getId(), normalizedName)) {
                throw new IllegalArgumentException("Service with name '" + normalizedName + "' already exists");
            }
        } else if (repository.existsByNameIgnoreCase(normalizedName)) {
            throw new IllegalArgumentException("Service with name '" + normalizedName + "' already exists");
        }

        MonitoredService service = new MonitoredService();
        service.setName(normalizedName);
        service.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        service.setEndpointUrl(request.getEndpointUrl().trim());
        service.setEnvironment(request.getEnvironment().trim().toUpperCase());
        service.setActive(true);
        if (currentUser != null) {
            service.setUser(currentUser);
        }

        return repository.save(service);
    }

    @Transactional(readOnly = true)
    public List<MonitoredService> getAllServices() {
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            if (isAdmin(currentUser)) {
                return repository.findAllWithUserByOrderByCreatedAtDesc();
            }
            return repository.findByUserIdWithUserOrderByCreatedAtDesc(currentUser.getId());
        }
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public MonitoredService getServiceById(Long id) {
        User currentUser = getCurrentUser();
        if (currentUser != null) {
            if (isAdmin(currentUser)) {
                return repository.findByIdWithUser(id)
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
        User currentUser = getCurrentUser();
        MonitoredService existingService;
        String normalizedName = request.getName().trim();

        if (currentUser != null) {
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
        existingService.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        existingService.setEndpointUrl(request.getEndpointUrl().trim());
        existingService.setEnvironment(request.getEnvironment().trim().toUpperCase());

        return repository.save(existingService);
    }

    @Transactional
    public MonitoredService updateServiceStatus(Long id, boolean active) {
        User currentUser = getCurrentUser();
        MonitoredService existingService;
        if (currentUser != null) {
            existingService = repository.findByIdAndUserId(id, currentUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
        } else {
            existingService = repository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Service not found with id: " + id));
        }
        existingService.setActive(active);
        return repository.save(existingService);
    }
}
