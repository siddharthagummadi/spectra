package com.spectra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

    @Mock
    private AnomalyEventRepository anomalyEventRepository;

    @Mock
    private ServiceMetricRepository metricRepository;

    @Mock
    private MonitoredServiceRepository serviceRepository;

    @Mock
    private StatisticalAnomalyDetector detector;

    @Mock
    private UserRepository userRepository;

    private AnomalyDetectionService service;

    private User testUser;
    private User adminUser;
    private MonitoredService userOwnedService;

    @BeforeEach
    void setUp() {
        service = new AnomalyDetectionService(
                anomalyEventRepository,
                metricRepository,
                serviceRepository,
                detector,
                userRepository
        );

        testUser = new User();
        testUser.setId(10L);
        testUser.setEmail("dev@spectra.local");
        testUser.setRole(Role.USER);

        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setEmail("admin@spectra.local");
        adminUser.setRole(Role.ADMIN);

        userOwnedService = new MonitoredService();
        userOwnedService.setId(100L);
        userOwnedService.setName("Order Service");
        userOwnedService.setUser(testUser);

        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(User user) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user.getEmail(), "password", List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @Test
    void detectAnomaly_MetricAlreadyEvaluated_IdempotentReturnsEmpty() {
        ServiceMetric metric = new ServiceMetric();
        metric.setId(500L);
        metric.setService(userOwnedService);
        metric.setMetricName("CPU_USAGE");
        metric.setMetricValue(90.0);

        when(anomalyEventRepository.existsByMetricId(500L)).thenReturn(true);

        Optional<AnomalyEvent> result = service.detectAnomaly(metric);
        assertTrue(result.isEmpty());
        verify(metricRepository, never()).findHistoricalBaselineBeforeId(any(), any(), any(), any(), any(), any());
    }

    @Test
    void detectAnomaly_NormalObservation_ReturnsEmpty() {
        ServiceMetric metric = new ServiceMetric();
        metric.setId(500L);
        metric.setService(userOwnedService);
        metric.setMetricName("CPU_USAGE");
        metric.setMetricValue(52.0);
        metric.setUnit("PERCENT");
        metric.setRecordedAt(Instant.now());

        when(anomalyEventRepository.existsByMetricId(500L)).thenReturn(false);

        List<ServiceMetric> baselineMetrics = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ServiceMetric m = new ServiceMetric();
            m.setMetricValue(50.0);
            baselineMetrics.add(m);
        }

        when(metricRepository.findHistoricalBaselineBeforeId(
                eq(100L), eq("CPU_USAGE"), eq("PERCENT"), eq(metric.getRecordedAt()), eq(500L), any(PageRequest.class)))
                .thenReturn(baselineMetrics);

        when(detector.detect(eq("CPU_USAGE"), eq(52.0), eq("PERCENT"), any()))
                .thenReturn(DetectionResult.normal(2.0, 50.0, 1.0));

        Optional<AnomalyEvent> result = service.detectAnomaly(metric);
        assertTrue(result.isEmpty());
        verify(anomalyEventRepository, never()).save(any());
    }

    @Test
    void detectAnomaly_AnomalousObservation_PersistsAndReturnsEvent() {
        ServiceMetric metric = new ServiceMetric();
        metric.setId(500L);
        metric.setService(userOwnedService);
        metric.setMetricName("CPU_USAGE");
        metric.setMetricValue(95.0);
        metric.setUnit("PERCENT");
        metric.setRecordedAt(Instant.now());

        when(anomalyEventRepository.existsByMetricId(500L)).thenReturn(false);

        List<ServiceMetric> baselineMetrics = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ServiceMetric m = new ServiceMetric();
            m.setMetricValue(50.0);
            baselineMetrics.add(m);
        }

        when(metricRepository.findHistoricalBaselineBeforeId(
                eq(100L), eq("CPU_USAGE"), eq("PERCENT"), eq(metric.getRecordedAt()), eq(500L), any(PageRequest.class)))
                .thenReturn(baselineMetrics);

        when(detector.detect(eq("CPU_USAGE"), eq(95.0), eq("PERCENT"), any()))
                .thenReturn(DetectionResult.anomaly(4.5, 50.0, 10.0, "HIGH", "POSITIVE_SPIKE", "High CPU spike"));

        when(anomalyEventRepository.save(any(AnomalyEvent.class))).thenAnswer(invocation -> {
            AnomalyEvent event = invocation.getArgument(0);
            event.setId(999L);
            return event;
        });

        Optional<AnomalyEvent> result = service.detectAnomaly(metric);
        assertTrue(result.isPresent());
        AnomalyEvent created = result.get();
        assertEquals(999L, created.getId());
        assertEquals("HIGH", created.getSeverity());
        assertEquals("POSITIVE_SPIKE", created.getDirection());
        assertEquals("DETECTED", created.getStatus());
        assertEquals(95.0, created.getObservedValue());
    }

    @Test
    void detectAnomaly_DataIntegrityViolationExceptionOnSave_ReturnsEmpty() {
        ServiceMetric metric = new ServiceMetric();
        metric.setId(500L);
        metric.setService(userOwnedService);
        metric.setMetricName("CPU_USAGE");
        metric.setMetricValue(95.0);
        metric.setUnit("PERCENT");
        metric.setRecordedAt(Instant.now());

        when(anomalyEventRepository.existsByMetricId(500L)).thenReturn(false);

        List<ServiceMetric> baselineMetrics = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ServiceMetric m = new ServiceMetric();
            m.setMetricValue(50.0);
            baselineMetrics.add(m);
        }

        when(metricRepository.findHistoricalBaselineBeforeId(
                eq(100L), eq("CPU_USAGE"), eq("PERCENT"), eq(metric.getRecordedAt()), eq(500L), any(PageRequest.class)))
                .thenReturn(baselineMetrics);

        when(detector.detect(eq("CPU_USAGE"), eq(95.0), eq("PERCENT"), any()))
                .thenReturn(DetectionResult.anomaly(4.5, 50.0, 10.0, "HIGH", "POSITIVE_SPIKE", "High CPU spike"));

        when(anomalyEventRepository.save(any(AnomalyEvent.class)))
                .thenThrow(new DataIntegrityViolationException("Unique index violation: uk_anomaly_events_metric_id"));

        Optional<AnomalyEvent> result = service.detectAnomaly(metric);
        assertTrue(result.isEmpty(), "Concurrent duplicate anomaly insertion should be caught and return Optional.empty()");
    }

    @Test
    void detectAnomaly_UnrelatedDataIntegrityViolationExceptionOnSave_RethrowsException() {
        ServiceMetric metric = new ServiceMetric();
        metric.setId(500L);
        metric.setService(userOwnedService);
        metric.setMetricName("CPU_USAGE");
        metric.setMetricValue(95.0);
        metric.setUnit("PERCENT");
        metric.setRecordedAt(Instant.now());

        when(anomalyEventRepository.existsByMetricId(500L)).thenReturn(false);

        List<ServiceMetric> baselineMetrics = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ServiceMetric m = new ServiceMetric();
            m.setMetricValue(50.0);
            baselineMetrics.add(m);
        }

        when(metricRepository.findHistoricalBaselineBeforeId(
                eq(100L), eq("CPU_USAGE"), eq("PERCENT"), eq(metric.getRecordedAt()), eq(500L), any(PageRequest.class)))
                .thenReturn(baselineMetrics);

        when(detector.detect(eq("CPU_USAGE"), eq(95.0), eq("PERCENT"), any()))
                .thenReturn(DetectionResult.anomaly(4.5, 50.0, 10.0, "HIGH", "POSITIVE_SPIKE", "High CPU spike"));

        when(anomalyEventRepository.save(any(AnomalyEvent.class)))
                .thenThrow(new DataIntegrityViolationException("NULL not allowed for column message"));

        assertThrows(DataIntegrityViolationException.class, () -> service.detectAnomaly(metric),
                "Unrelated DataIntegrityViolationException must NOT be silently swallowed as a duplicate");
    }

    @Test
    void getRecentAnomalies_AdminUser_ReturnsAllAnomalies() {
        authenticateAs(adminUser);

        when(anomalyEventRepository.findAllByOrderByDetectedAtDescIdDesc(any(PageRequest.class)))
                .thenReturn(List.of(new AnomalyEvent(), new AnomalyEvent()));

        List<AnomalyEvent> anomalies = service.getRecentAnomalies(50);
        assertEquals(2, anomalies.size());
        verify(anomalyEventRepository).findAllByOrderByDetectedAtDescIdDesc(PageRequest.of(0, 50));
    }

    @Test
    void getRecentAnomalies_RegularUser_ReturnsOwnedAnomaliesOnly() {
        authenticateAs(testUser);

        when(anomalyEventRepository.findByServiceUserIdOrderByDetectedAtDescIdDesc(eq(10L), any(PageRequest.class)))
                .thenReturn(List.of(new AnomalyEvent()));

        List<AnomalyEvent> anomalies = service.getRecentAnomalies(20);
        assertEquals(1, anomalies.size());
        verify(anomalyEventRepository).findByServiceUserIdOrderByDetectedAtDescIdDesc(10L, PageRequest.of(0, 20));
    }

    @Test
    void getRecentAnomalies_InvalidLimit_ThrowsBadRequestException() {
        assertThrows(BadRequestException.class, () -> service.getRecentAnomalies(0));
        assertThrows(BadRequestException.class, () -> service.getRecentAnomalies(101));
    }

    @Test
    void getAnomaliesByServiceId_ServiceNotFound_ThrowsResourceNotFound() {
        authenticateAs(testUser);
        when(serviceRepository.existsByIdAndUserId(999L, 10L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.getAnomaliesByServiceId(999L, 50));
    }

    @Test
    void getAnomaliesByServiceId_UserRole_ReturnsOwnedServiceAnomalies() {
        authenticateAs(testUser);
        when(serviceRepository.existsByIdAndUserId(100L, 10L)).thenReturn(true);
        when(anomalyEventRepository.findByServiceIdAndServiceUserIdOrderByDetectedAtDescIdDesc(eq(100L), eq(10L), any(PageRequest.class)))
                .thenReturn(List.of(new AnomalyEvent()));

        List<AnomalyEvent> result = service.getAnomaliesByServiceId(100L, 50);
        assertEquals(1, result.size());
    }

    @Test
    void getAnomalyById_NotFound_ThrowsResourceNotFound() {
        authenticateAs(testUser);
        when(anomalyEventRepository.findByIdAndServiceUserId(123L, 10L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getAnomalyById(123L));
    }

    @Test
    void acknowledgeAnomaly_ChangesStatusAndSetsTimestamp() {
        authenticateAs(testUser);

        AnomalyEvent event = new AnomalyEvent();
        event.setId(123L);
        event.setStatus("DETECTED");

        when(anomalyEventRepository.findByIdAndServiceUserId(123L, 10L)).thenReturn(Optional.of(event));
        when(anomalyEventRepository.save(any(AnomalyEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AnomalyEvent result = service.acknowledgeAnomaly(123L);
        assertEquals("ACKNOWLEDGED", result.getStatus());
        assertEquals("dev@spectra.local", result.getAcknowledgedBy());
        assertNotNull(result.getAcknowledgedAt());
    }

    @Test
    void acknowledgeAnomaly_AlreadyAcknowledged_IsIdempotent() {
        authenticateAs(testUser);

        Instant originalTime = Instant.now().minusSeconds(100);
        AnomalyEvent event = new AnomalyEvent();
        event.setId(123L);
        event.setStatus("ACKNOWLEDGED");
        event.setAcknowledgedAt(originalTime);
        event.setAcknowledgedBy("dev@spectra.local");

        when(anomalyEventRepository.findByIdAndServiceUserId(123L, 10L)).thenReturn(Optional.of(event));

        AnomalyEvent result = service.acknowledgeAnomaly(123L);
        assertEquals("ACKNOWLEDGED", result.getStatus());
        assertEquals(originalTime, result.getAcknowledgedAt());
        verify(anomalyEventRepository, never()).save(any());
    }
}
