package com.spectra.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.spectra.dto.ConfirmIngestionRequest;
import com.spectra.dto.ConfirmIngestionResponse;
import com.spectra.dto.IngestionPreviewResponse;
import com.spectra.entity.IngestionBatch;
import com.spectra.entity.MonitoredService;
import com.spectra.entity.Role;
import com.spectra.entity.ServiceMetric;
import com.spectra.entity.User;
import com.spectra.exception.BadRequestException;
import com.spectra.repository.AlertEventRepository;
import com.spectra.repository.AnomalyEventRepository;
import com.spectra.repository.AuditLogRepository;
import com.spectra.repository.IncidentRepository;
import com.spectra.repository.IngestionBatchRepository;
import com.spectra.repository.MonitoredServiceRepository;
import com.spectra.repository.ServiceMetricRepository;

public class IngestionServiceTest {

    @Mock private IngestionBatchRepository batchRepository;
    @Mock private ServiceMetricRepository metricRepository;
    @Mock private MonitoredServiceRepository serviceRepository;
    @Mock private AlertEventRepository alertEventRepository;
    @Mock private AnomalyEventRepository anomalyEventRepository;
    @Mock private IncidentRepository incidentRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ServiceMetricService metricService;
    @Mock private AlertService alertService;
    @Mock private AnomalyDetectionService anomalyDetectionService;

    private IngestionService ingestionService;
    private User testUser;
    private MonitoredService testService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ingestionService = new IngestionService(
                batchRepository,
                metricRepository,
                serviceRepository,
                alertEventRepository,
                anomalyEventRepository,
                incidentRepository,
                auditLogRepository,
                metricService,
                alertService,
                anomalyDetectionService
        );

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("operator@spectra.local");
        testUser.setRole(Role.ADMIN);

        testService = new MonitoredService();
        testService.setId(10L);
        testService.setName("Payment-Service");
        testService.setEnvironment("PRODUCTION");
    }

    @Test
    void previewDataset_ValidNarrowCsv_ReturnsParsedObservations() {
        String csv = "service_name,metric_name,metric_value,unit,timestamp\n" +
                     "Payment-Service,CPU_USAGE,45.5,PERCENT,2026-11-08T10:00:00Z\n" +
                     "Payment-Service,MEMORY_USAGE,72.0,PERCENT,2026-11-08T10:01:00Z\n";

        when(serviceRepository.findAll()).thenReturn(List.of(testService));
        when(batchRepository.findByBatchKey(any())).thenReturn(Optional.empty());

        IngestionPreviewResponse preview = ingestionService.previewDataset("test.csv", csv, testUser);

        assertNotNull(preview);
        assertEquals(2, preview.getTotalRecords());
        assertEquals(2, preview.getValidRecords());
        assertEquals(0, preview.getInvalidRecords());
        assertFalse(preview.isDuplicateBatch());
        assertEquals("CPU_USAGE", preview.getPreviewRows().get(0).getMetricName());
        assertEquals(45.5, preview.getPreviewRows().get(0).getMetricValue());
    }

    @Test
    void previewDataset_ValidWideCsv_ExtractsMultipleMetrics() {
        String csv = "service_name,cpu_usage,memory_usage,response_time_ms,error_rate_pct\n" +
                     "Payment-Service,50.0,65.0,120.0,0.5\n";

        when(serviceRepository.findAll()).thenReturn(List.of(testService));
        when(batchRepository.findByBatchKey(any())).thenReturn(Optional.empty());

        IngestionPreviewResponse preview = ingestionService.previewDataset("wide.csv", csv, testUser);

        assertNotNull(preview);
        assertEquals(4, preview.getTotalRecords());
        assertEquals(4, preview.getValidRecords());
    }

    @Test
    void previewDataset_DuplicateBatchKey_WarnsDuplicate() {
        String csv = "service_name,metric_name,metric_value\nPayment-Service,CPU_USAGE,50.0\n";
        IngestionBatch existing = new IngestionBatch();
        existing.setId(99L);
        existing.setUploaderEmail("admin@spectra.local");
        existing.setCreatedAt(Instant.now());

        when(serviceRepository.findAll()).thenReturn(List.of(testService));
        when(batchRepository.findByBatchKey(any())).thenReturn(Optional.of(existing));

        IngestionPreviewResponse preview = ingestionService.previewDataset("dup.csv", csv, testUser);
        assertTrue(preview.isDuplicateBatch());
        assertNotNull(preview.getDuplicateWarning());
    }

    @Test
    void confirmIngestion_DuplicateCompletedBatch_ThrowsBadRequest() {
        ConfirmIngestionRequest req = new ConfirmIngestionRequest();
        req.setBatchKey("existing-key");
        req.setRecords(List.of(new IngestionPreviewResponse.TelemetryObservationDto()));

        IngestionBatch existing = new IngestionBatch();
        existing.setStatus("COMPLETED");
        when(batchRepository.findByBatchKey("existing-key")).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class, () -> ingestionService.confirmIngestion(req, testUser));
    }

    @Test
    void confirmIngestion_ValidRecords_PersistsAndTriggersPipeline() {
        IngestionPreviewResponse.TelemetryObservationDto obs = new IngestionPreviewResponse.TelemetryObservationDto();
        obs.setServiceId(10L);
        obs.setMetricName("CPU_USAGE");
        obs.setMetricValue(88.0);
        obs.setUnit("PERCENT");
        obs.setTimestamp(Instant.now().toString());

        ConfirmIngestionRequest req = new ConfirmIngestionRequest();
        req.setFilename("telemetry.csv");
        req.setRecords(List.of(obs));

        when(batchRepository.findByBatchKey(any())).thenReturn(Optional.empty());
        when(serviceRepository.findById(10L)).thenReturn(Optional.of(testService));
        when(batchRepository.save(any(IngestionBatch.class))).thenAnswer(i -> {
            IngestionBatch b = i.getArgument(0);
            b.setId(101L);
            return b;
        });
        when(metricRepository.save(any(ServiceMetric.class))).thenAnswer(i -> {
            ServiceMetric m = i.getArgument(0);
            m.setId(500L);
            return m;
        });

        ConfirmIngestionResponse res = ingestionService.confirmIngestion(req, testUser);

        assertNotNull(res);
        assertEquals(1, res.getTotalIngested());
        assertEquals("COMPLETED", res.getStatus());
        verify(alertService).evaluateMetric(any(ServiceMetric.class));
        verify(anomalyDetectionService).detectAnomaly(any(ServiceMetric.class));
        verify(auditLogRepository).save(any());
    }

    @Test
    void deleteBatch_ValidBatch_DeletesMetricsAndPreservesIncidents() {
        IngestionBatch batch = new IngestionBatch();
        batch.setId(101L);
        batch.setFilename("batch.csv");
        batch.setUser(testUser);

        ServiceMetric metric = new ServiceMetric();
        metric.setId(500L);
        metric.setService(testService);
        metric.setMetricName("CPU_USAGE");
        metric.setRecordedAt(Instant.now());

        when(batchRepository.findByIdAndDeletedAtIsNull(101L)).thenReturn(Optional.of(batch));
        when(metricRepository.findByIngestionBatchId(101L)).thenReturn(List.of(metric));

        ingestionService.deleteBatch(101L, testUser);

        verify(anomalyEventRepository).deleteByMetricIdIn(List.of(500L));
        verify(metricRepository).deleteByIngestionBatchId(101L);
        verify(batchRepository).save(batch);
        verify(auditLogRepository).save(any());
        assertEquals("DELETED", batch.getStatus());
        assertNotNull(batch.getDeletedAt());
    }
}
