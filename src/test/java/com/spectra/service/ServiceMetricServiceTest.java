package com.spectra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.spectra.dto.CreateMetricRequest;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.ServiceMetric;
import com.spectra.exception.BadRequestException;
import com.spectra.exception.ResourceNotFoundException;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.ServiceMetricRepository;

@ExtendWith(MockitoExtension.class)
class ServiceMetricServiceTest {

    @Mock
    private ServiceMetricRepository metricRepository;

    @Mock
    private MonitoredServiceRepository serviceRepository;

    @Mock
    private AlertService alertService;

    @Mock
    private AnomalyDetectionService anomalyDetectionService;

    @InjectMocks
    private ServiceMetricService service;

    @Test
    void submitMetric_CpuUsage_InfersPercentAndSucceeds() {
        MonitoredService monitoredService = new MonitoredService();
        monitoredService.setId(1L);
        monitoredService.setName("Order Service");

        CreateMetricRequest request = new CreateMetricRequest();
        request.setServiceId(1L);
        request.setMetricName("  cpu_usage  ");
        request.setMetricValue(45.5);

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(monitoredService));
        when(metricRepository.save(any(ServiceMetric.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceMetric result = service.submitMetric(request);

        assertNotNull(result);
        assertEquals("CPU_USAGE", result.getMetricName());
        assertEquals(45.5, result.getMetricValue());
        assertEquals("PERCENT", result.getUnit());
        assertNotNull(result.getRecordedAt());

        verify(metricRepository).save(any(ServiceMetric.class));
    }

    @Test
    void submitMetric_ErrorRate_InfersPercentAndSucceeds() {
        MonitoredService monitoredService = new MonitoredService();
        monitoredService.setId(1L);

        CreateMetricRequest request = new CreateMetricRequest();
        request.setServiceId(1L);
        request.setMetricName("ERROR_RATE");
        request.setMetricValue(2.5);

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(monitoredService));
        when(metricRepository.save(any(ServiceMetric.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceMetric result = service.submitMetric(request);

        assertEquals("PERCENT", result.getUnit());
        assertEquals(2.5, result.getMetricValue());
    }

    @Test
    void submitMetric_ResponseTime_InfersMillisecondsAndSucceeds() {
        MonitoredService monitoredService = new MonitoredService();
        monitoredService.setId(1L);

        CreateMetricRequest request = new CreateMetricRequest();
        request.setServiceId(1L);
        request.setMetricName("RESPONSE_TIME");
        request.setMetricValue(120.0);

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(monitoredService));
        when(metricRepository.save(any(ServiceMetric.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceMetric result = service.submitMetric(request);

        assertEquals("MILLISECONDS", result.getUnit());
        assertEquals(120.0, result.getMetricValue());
    }

    @Test
    void submitMetric_MemoryUsage_WithMegabytes_Succeeds() {
        MonitoredService monitoredService = new MonitoredService();
        monitoredService.setId(1L);

        CreateMetricRequest request = new CreateMetricRequest();
        request.setServiceId(1L);
        request.setMetricName("MEMORY_USAGE");
        request.setMetricValue(512.0);
        request.setUnit("MEGABYTES");

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(monitoredService));
        when(metricRepository.save(any(ServiceMetric.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServiceMetric result = service.submitMetric(request);

        assertEquals("MEGABYTES", result.getUnit());
        assertEquals(512.0, result.getMetricValue());
    }

    @Test
    void submitMetric_AnomalyDetectionThrowsException_MetricSubmissionStillSucceeds() {
        MonitoredService monitoredService = new MonitoredService();
        monitoredService.setId(1L);

        CreateMetricRequest request = new CreateMetricRequest();
        request.setServiceId(1L);
        request.setMetricName("CPU_USAGE");
        request.setMetricValue(75.0);

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(monitoredService));
        when(metricRepository.save(any(ServiceMetric.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(anomalyDetectionService.detectAnomaly(any(ServiceMetric.class)))
                .thenThrow(new RuntimeException("Simulated anomaly detection service failure"));

        ServiceMetric result = service.submitMetric(request);

        assertNotNull(result);
        assertEquals("CPU_USAGE", result.getMetricName());
        assertEquals(75.0, result.getMetricValue());
        verify(metricRepository).save(any(ServiceMetric.class));
        verify(alertService).evaluateMetric(any(ServiceMetric.class));
    }

    @Test
    void submitMetric_MemoryUsage_MissingUnit_ThrowsBadRequestException() {
        MonitoredService monitoredService = new MonitoredService();
        monitoredService.setId(1L);

        CreateMetricRequest request = new CreateMetricRequest();
        request.setServiceId(1L);
        request.setMetricName("MEMORY_USAGE");
        request.setMetricValue(512.0);

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(monitoredService));

        assertThrows(BadRequestException.class, () -> service.submitMetric(request));
    }

    @Test
    void submitMetric_InvalidMetricName_ThrowsBadRequestException() {
        MonitoredService monitoredService = new MonitoredService();
        monitoredService.setId(1L);

        CreateMetricRequest request = new CreateMetricRequest();
        request.setServiceId(1L);
        request.setMetricName("UNKNOWN_METRIC");
        request.setMetricValue(10.0);

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(monitoredService));

        assertThrows(BadRequestException.class, () -> service.submitMetric(request));
    }

    @Test
    void submitMetric_NaNValue_ThrowsBadRequestException() {
        MonitoredService monitoredService = new MonitoredService();
        monitoredService.setId(1L);

        CreateMetricRequest request = new CreateMetricRequest();
        request.setServiceId(1L);
        request.setMetricName("CPU_USAGE");
        request.setMetricValue(Double.NaN);

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(monitoredService));

        assertThrows(BadRequestException.class, () -> service.submitMetric(request));
    }

    @Test
    void submitMetric_InfiniteValue_ThrowsBadRequestException() {
        MonitoredService monitoredService = new MonitoredService();
        monitoredService.setId(1L);

        CreateMetricRequest request = new CreateMetricRequest();
        request.setServiceId(1L);
        request.setMetricName("CPU_USAGE");
        request.setMetricValue(Double.POSITIVE_INFINITY);

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(monitoredService));

        assertThrows(BadRequestException.class, () -> service.submitMetric(request));
    }

    @Test
    void submitMetric_NegativeValue_ThrowsBadRequestException() {
        MonitoredService monitoredService = new MonitoredService();
        monitoredService.setId(1L);

        CreateMetricRequest request = new CreateMetricRequest();
        request.setServiceId(1L);
        request.setMetricName("CPU_USAGE");
        request.setMetricValue(-5.0);

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(monitoredService));

        assertThrows(BadRequestException.class, () -> service.submitMetric(request));
    }

    @Test
    void submitMetric_PercentAbove100_ThrowsBadRequestException() {
        MonitoredService monitoredService = new MonitoredService();
        monitoredService.setId(1L);

        CreateMetricRequest request = new CreateMetricRequest();
        request.setServiceId(1L);
        request.setMetricName("CPU_USAGE");
        request.setMetricValue(105.0);

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(monitoredService));

        assertThrows(BadRequestException.class, () -> service.submitMetric(request));
    }

    @Test
    void submitMetric_FutureTimestamp_ThrowsBadRequestException() {
        MonitoredService monitoredService = new MonitoredService();
        monitoredService.setId(1L);

        CreateMetricRequest request = new CreateMetricRequest();
        request.setServiceId(1L);
        request.setMetricName("CPU_USAGE");
        request.setMetricValue(50.0);
        request.setRecordedAt(Instant.now().plus(2, ChronoUnit.HOURS));

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(monitoredService));

        assertThrows(BadRequestException.class, () -> service.submitMetric(request));
    }

    @Test
    void submitMetric_NonexistentService_ThrowsResourceNotFoundException() {
        CreateMetricRequest request = new CreateMetricRequest();
        request.setServiceId(999L);
        request.setMetricName("CPU_USAGE");
        request.setMetricValue(50.0);

        when(serviceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.submitMetric(request));
    }

    @Test
    void getMetricsByServiceId_Success() {
        when(serviceRepository.existsById(1L)).thenReturn(true);

        ServiceMetric metric = new ServiceMetric();
        metric.setId(10L);
        metric.setMetricName("CPU_USAGE");
        metric.setMetricValue(40.0);

        when(metricRepository.findByServiceIdOrderByRecordedAtDescIdDesc(eq(1L), any(PageRequest.class)))
                .thenReturn(List.of(metric));

        List<ServiceMetric> result = service.getMetricsByServiceId(1L, 20);

        assertEquals(1, result.size());
        assertEquals("CPU_USAGE", result.get(0).getMetricName());
    }

    @Test
    void getMetricsByServiceId_NonexistentService_ThrowsResourceNotFoundException() {
        when(serviceRepository.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.getMetricsByServiceId(99L, 50));
    }

    @Test
    void getMetricsByServiceId_InvalidLimit_ThrowsBadRequestException() {
        when(serviceRepository.existsById(1L)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.getMetricsByServiceId(1L, 0));
        assertThrows(BadRequestException.class, () -> service.getMetricsByServiceId(1L, 101));
    }

    @Test
    void getRecentMetrics_Success() {
        ServiceMetric metric = new ServiceMetric();
        metric.setId(10L);
        metric.setMetricName("RESPONSE_TIME");

        when(metricRepository.findAllByOrderByRecordedAtDescIdDesc(any(PageRequest.class)))
                .thenReturn(List.of(metric));

        List<ServiceMetric> result = service.getRecentMetrics(50);

        assertEquals(1, result.size());
        assertEquals("RESPONSE_TIME", result.get(0).getMetricName());
    }

    @Test
    void getRecentMetrics_InvalidLimit_ThrowsBadRequestException() {
        assertThrows(BadRequestException.class, () -> service.getRecentMetrics(-5));
        assertThrows(BadRequestException.class, () -> service.getRecentMetrics(150));
    }
}
