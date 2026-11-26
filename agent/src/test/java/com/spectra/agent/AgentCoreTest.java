package com.spectra.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentCoreTest {

    @TempDir
    Path tempDir;

    @Test
    void localBufferStore_PersistsBoundedQueueAndRemovesByBatchId() throws Exception {
        LocalBufferStore store = new LocalBufferStore(tempDir, 10);
        for (int i = 0; i < 12; i++) {
            AgentModels.TelemetryBatch batch = new AgentModels.TelemetryBatch(
                    "batch-" + i,
                    List.of(new AgentModels.TelemetrySample(
                            "Host-Infra", "PRODUCTION", "http://host.internal",
                            "CPU_USAGE", 40.0 + i, "PERCENT", Instant.now()))
            );
            store.enqueue(batch);
        }

        // Bounded capacity = 10 drops oldest 2
        List<AgentModels.TelemetryBatch> all = store.readAll();
        assertEquals(10, all.size());
        assertEquals("batch-2", all.get(0).clientBatchId());

        store.removeByBatchId("batch-5");
        assertEquals(9, store.size());
    }

    @Test
    void telemetryCollector_EmitsAllCoreInfrastructureMetrics() {
        AgentConfig config = new AgentConfig(
                "http://localhost:8080",
                "",
                "agent-test",
                "Test-Host",
                "PRODUCTION",
                30,
                30,
                tempDir,
                Map.of()
        );
        TelemetryCollector collector = new TelemetryCollector();
        List<AgentModels.TelemetrySample> samples = collector.collect(config);

        List<String> names = samples.stream().map(AgentModels.TelemetrySample::metricName).toList();
        assertTrue(names.contains("CPU_USAGE"));
        assertTrue(names.contains("MEMORY_USAGE"));
        assertTrue(names.contains("DISK_USAGE"));
        assertTrue(names.contains("NETWORK_USAGE"));
        assertTrue(names.contains("SYSTEM_UPTIME"));
        assertFalse(samples.isEmpty());
    }

    @Test
    void exponentialBackoff_ScalesAndCapsAt60Seconds() {
        assertEquals(2000L, SpectraApiClient.computeBackoffMs(0));
        assertEquals(4000L, SpectraApiClient.computeBackoffMs(1));
        assertEquals(8000L, SpectraApiClient.computeBackoffMs(2));
        assertEquals(60_000L, SpectraApiClient.computeBackoffMs(10));
    }
}
