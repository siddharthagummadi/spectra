package com.spectra.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.spectra.dto.CreateServiceRequest;
import com.spectra.dto.ServiceResponse;
import com.spectra.dto.UpdateServiceRequest;
import com.spectra.entity.MonitoredService;
import com.spectra.service.MonitoredServiceService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/services")
@Validated
public class MonitoredServiceController {

    private final MonitoredServiceService service;

    public MonitoredServiceController(MonitoredServiceService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ServiceResponse> createService(@Valid @RequestBody CreateServiceRequest request) {
        MonitoredService createdService = service.createService(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ServiceResponse.from(createdService));
    }

    @GetMapping
    public ResponseEntity<List<ServiceResponse>> getAllServices() {
        List<ServiceResponse> services = service.getAllServices()
                .stream()
                .map(ServiceResponse::from)
                .toList();
        return ResponseEntity.ok(services);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceResponse> getServiceById(@PathVariable Long id) {
        MonitoredService monitoredService = service.getServiceById(id);
        return ResponseEntity.ok(ServiceResponse.from(monitoredService));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceResponse> updateService(
            @PathVariable Long id,
            @Valid @RequestBody UpdateServiceRequest request) {
        MonitoredService updatedService = service.updateService(id, request);
        return ResponseEntity.ok(ServiceResponse.from(updatedService));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ServiceResponse> updateServiceStatus(
            @PathVariable Long id,
            @RequestParam boolean active) {
        MonitoredService updatedService = service.updateServiceStatus(id, active);
        return ResponseEntity.ok(ServiceResponse.from(updatedService));
    }
}
