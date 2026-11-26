package com.spectra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.spectra.dto.CreateServiceRequest;
import com.spectra.dto.UpdateServiceRequest;
import com.spectra.entity.MonitoredService;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.MonitoredServiceRepository;

@ExtendWith(MockitoExtension.class)
class MonitoredServiceServiceTest {

    @Mock
    private MonitoredServiceRepository repository;

    @InjectMocks
    private MonitoredServiceService service;

    @Test
    void createService_Success() {
        CreateServiceRequest request = new CreateServiceRequest();
        request.setName("  Payment API  ");
        request.setDescription("Handles payment transactions");
        request.setEndpointUrl("https://payment.example.com/health");
        request.setEnvironment("PRODUCTION");

        when(repository.existsByNameIgnoreCase("Payment API")).thenReturn(false);
        when(repository.save(any(MonitoredService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MonitoredService created = service.createService(request);

        assertNotNull(created);
        assertEquals("Payment API", created.getName());
        assertEquals("Handles payment transactions", created.getDescription());
        assertEquals("https://payment.example.com/health", created.getEndpointUrl());
        assertEquals("PRODUCTION", created.getEnvironment());
        assertTrue(created.isActive());

        verify(repository).save(any(MonitoredService.class));
    }

    @Test
    void createService_DuplicateName_ThrowsException() {
        CreateServiceRequest request = new CreateServiceRequest();
        request.setName("Payment API");
        request.setEndpointUrl("https://payment.example.com/health");
        request.setEnvironment("PRODUCTION");

        when(repository.existsByNameIgnoreCase("Payment API")).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.createService(request)
        );

        assertEquals("Service with name 'Payment API' already exists", exception.getMessage());
    }

    @Test
    void getAllServices_ReturnsList() {
        MonitoredService serviceEntity = new MonitoredService();
        serviceEntity.setId(1L);
        serviceEntity.setName("Auth Service");

        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(serviceEntity));

        List<MonitoredService> result = service.getAllServices();

        assertEquals(1, result.size());
        assertEquals("Auth Service", result.get(0).getName());
    }

    @Test
    void getServiceById_Success() {
        MonitoredService serviceEntity = new MonitoredService();
        serviceEntity.setId(1L);
        serviceEntity.setName("Auth Service");

        when(repository.findById(1L)).thenReturn(Optional.of(serviceEntity));

        MonitoredService result = service.getServiceById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Auth Service", result.getName());
    }

    @Test
    void getServiceById_NotFound_ThrowsException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.getServiceById(99L)
        );

        assertEquals("Service not found with id: 99", exception.getMessage());
    }

    @Test
    void updateService_Success() {
        MonitoredService existing = new MonitoredService();
        existing.setId(1L);
        existing.setName("Old Name");
        existing.setDescription("Old Description");
        existing.setEndpointUrl("https://old.example.com");
        existing.setEnvironment("DEVELOPMENT");

        UpdateServiceRequest request = new UpdateServiceRequest();
        request.setName("  New Name  ");
        request.setDescription("New Description");
        request.setEndpointUrl("https://new.example.com");
        request.setEnvironment("STAGING");

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByNameIgnoreCaseAndIdNot("New Name", 1L)).thenReturn(false);
        when(repository.save(any(MonitoredService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MonitoredService updated = service.updateService(1L, request);

        assertEquals("New Name", updated.getName());
        assertEquals("New Description", updated.getDescription());
        assertEquals("https://new.example.com", updated.getEndpointUrl());
        assertEquals("STAGING", updated.getEnvironment());
    }

    @Test
    void updateService_DuplicateNameOtherId_ThrowsException() {
        MonitoredService existing = new MonitoredService();
        existing.setId(1L);
        existing.setName("Existing Service");

        UpdateServiceRequest request = new UpdateServiceRequest();
        request.setName("Taken Service Name");
        request.setEndpointUrl("https://service.example.com");
        request.setEnvironment("PRODUCTION");

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByNameIgnoreCaseAndIdNot("Taken Service Name", 1L)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateService(1L, request)
        );

        assertEquals("Service with name 'Taken Service Name' already exists", exception.getMessage());
    }

    @Test
    void updateService_SameNameSameId_Success() {
        MonitoredService existing = new MonitoredService();
        existing.setId(1L);
        existing.setName("Payment API");
        existing.setEndpointUrl("https://old.example.com");
        existing.setEnvironment("DEVELOPMENT");

        UpdateServiceRequest request = new UpdateServiceRequest();
        request.setName("Payment API");
        request.setEndpointUrl("https://new.example.com");
        request.setEnvironment("PRODUCTION");

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.existsByNameIgnoreCaseAndIdNot("Payment API", 1L)).thenReturn(false);
        when(repository.save(any(MonitoredService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MonitoredService updated = service.updateService(1L, request);

        assertEquals("Payment API", updated.getName());
        assertEquals("https://new.example.com", updated.getEndpointUrl());
    }

    @Test
    void updateServiceStatus_Success() {
        MonitoredService existing = new MonitoredService();
        existing.setId(1L);
        existing.setActive(true);

        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(MonitoredService.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MonitoredService updated = service.updateServiceStatus(1L, false);

        assertFalse(updated.isActive());
    }
}
