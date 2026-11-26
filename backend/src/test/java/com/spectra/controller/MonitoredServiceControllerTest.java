package com.spectra.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.spectra.dto.CreateServiceRequest;
import com.spectra.dto.UpdateServiceRequest;
import com.spectra.entity.MonitoredService;
import com.spectra.exception.GlobalExceptionHandler;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.service.MonitoredServiceService;

@ExtendWith(MockitoExtension.class)
class MonitoredServiceControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MonitoredServiceService service;

    @InjectMocks
    private MonitoredServiceController controller;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createService_ValidPayload_ReturnsCreated() throws Exception {
        MonitoredService serviceEntity = new MonitoredService();
        serviceEntity.setId(1L);
        serviceEntity.setName("Auth Service");
        serviceEntity.setDescription("Handles authentication");
        serviceEntity.setEndpointUrl("https://auth.example.com/health");
        serviceEntity.setEnvironment("PRODUCTION");
        serviceEntity.setActive(true);

        when(service.createService(any(CreateServiceRequest.class))).thenReturn(serviceEntity);

        String jsonPayload = """
                {
                    "name": "Auth Service",
                    "description": "Handles authentication",
                    "endpointUrl": "https://auth.example.com/health",
                    "environment": "PRODUCTION"
                }
                """;

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Auth Service"))
                .andExpect(jsonPath("$.endpointUrl").value("https://auth.example.com/health"))
                .andExpect(jsonPath("$.environment").value("PRODUCTION"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createService_InvalidUrl_ReturnsBadRequest() throws Exception {
        String jsonPayload = """
                {
                    "name": "Auth Service",
                    "endpointUrl": "ftp://invalid-protocol.com",
                    "environment": "PRODUCTION"
                }
                """;

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.endpointUrl").exists());
    }

    @Test
    void createService_InvalidEnvironment_ReturnsBadRequest() throws Exception {
        String jsonPayload = """
                {
                    "name": "Auth Service",
                    "endpointUrl": "https://auth.example.com",
                    "environment": "UNKNOWN_ENV"
                }
                """;

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.environment").exists());
    }

    @Test
    void createService_BlankName_ReturnsBadRequest() throws Exception {
        String jsonPayload = """
                {
                    "name": "   ",
                    "endpointUrl": "https://auth.example.com",
                    "environment": "PRODUCTION"
                }
                """;

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void createService_DuplicateName_ReturnsConflict() throws Exception {
        when(service.createService(any(CreateServiceRequest.class)))
                .thenThrow(new IllegalArgumentException("Service with name 'Auth Service' already exists"));

        String jsonPayload = """
                {
                    "name": "Auth Service",
                    "endpointUrl": "https://auth.example.com",
                    "environment": "PRODUCTION"
                }
                """;

        mockMvc.perform(post("/api/services")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Service with name 'Auth Service' already exists"));
    }

    @Test
    void getAllServices_ReturnsOkAndList() throws Exception {
        MonitoredService serviceEntity = new MonitoredService();
        serviceEntity.setId(1L);
        serviceEntity.setName("Auth Service");
        serviceEntity.setEndpointUrl("https://auth.example.com");
        serviceEntity.setEnvironment("PRODUCTION");
        serviceEntity.setActive(true);

        when(service.getAllServices()).thenReturn(List.of(serviceEntity));

        mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Auth Service"));
    }

    @Test
    void getServiceById_ExistingId_ReturnsOk() throws Exception {
        MonitoredService serviceEntity = new MonitoredService();
        serviceEntity.setId(1L);
        serviceEntity.setName("Auth Service");
        serviceEntity.setEndpointUrl("https://auth.example.com");
        serviceEntity.setEnvironment("PRODUCTION");
        serviceEntity.setActive(true);

        when(service.getServiceById(1L)).thenReturn(serviceEntity);

        mockMvc.perform(get("/api/services/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Auth Service"));
    }

    @Test
    void getServiceById_NonexistentId_ReturnsNotFound() throws Exception {
        when(service.getServiceById(99L))
                .thenThrow(new ResourceNotFoundException("Service not found with id: 99"));

        mockMvc.perform(get("/api/services/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Service not found with id: 99"));
    }

    @Test
    void updateService_ValidPayload_ReturnsOk() throws Exception {
        MonitoredService updated = new MonitoredService();
        updated.setId(1L);
        updated.setName("Updated Service");
        updated.setEndpointUrl("https://updated.example.com");
        updated.setEnvironment("STAGING");
        updated.setActive(true);

        when(service.updateService(eq(1L), any(UpdateServiceRequest.class))).thenReturn(updated);

        String jsonPayload = """
                {
                    "name": "Updated Service",
                    "endpointUrl": "https://updated.example.com",
                    "environment": "STAGING"
                }
                """;

        mockMvc.perform(put("/api/services/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Updated Service"))
                .andExpect(jsonPath("$.environment").value("STAGING"));
    }

    @Test
    void updateService_DuplicateName_ReturnsConflict() throws Exception {
        when(service.updateService(eq(1L), any(UpdateServiceRequest.class)))
                .thenThrow(new IllegalArgumentException("Service with name 'Existing Name' already exists"));

        String jsonPayload = """
                {
                    "name": "Existing Name",
                    "endpointUrl": "https://updated.example.com",
                    "environment": "STAGING"
                }
                """;

        mockMvc.perform(put("/api/services/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void updateService_NonexistentId_ReturnsNotFound() throws Exception {
        when(service.updateService(eq(99L), any(UpdateServiceRequest.class)))
                .thenThrow(new ResourceNotFoundException("Service not found with id: 99"));

        String jsonPayload = """
                {
                    "name": "Updated Service",
                    "endpointUrl": "https://updated.example.com",
                    "environment": "STAGING"
                }
                """;

        mockMvc.perform(put("/api/services/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateServiceStatus_ReturnsOk() throws Exception {
        MonitoredService updated = new MonitoredService();
        updated.setId(1L);
        updated.setName("Auth Service");
        updated.setEndpointUrl("https://auth.example.com");
        updated.setEnvironment("PRODUCTION");
        updated.setActive(false);

        when(service.updateServiceStatus(1L, false)).thenReturn(updated);

        mockMvc.perform(patch("/api/services/1/status")
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.active").value(false));
    }
}
