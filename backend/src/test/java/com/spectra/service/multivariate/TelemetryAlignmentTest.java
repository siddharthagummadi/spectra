package com.spectra.service.multivariate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TelemetryAlignmentTest {

    private MultivariateAlignmentService alignmentService;

    @BeforeEach
    void setUp() {
        alignmentService = new MultivariateAlignmentService();
    }

    @Test
    void asynchronousArrival_AlignsCompleteVectorSuccessfully() {
        Long serviceId = 101L;
        Instant t0 = Instant.parse("2026-11-12T12:00:00Z");

        // 1. CPU arrives at t0
        TelemetryAlignmentResult res1 = alignmentService.ingestAndAlign(
            serviceId, "CPU_USAGE", 45.0, t0);
        assertEquals(TelemetryAlignmentResult.Status.INCOMPLETE, res1.status());
        assertFalse(res1.isAligned());

        // 2. Memory arrives at t0 + 2s
        TelemetryAlignmentResult res2 = alignmentService.ingestAndAlign(
            serviceId, "MEMORY_USAGE", 60.0, t0.plusSeconds(2));
        assertEquals(TelemetryAlignmentResult.Status.INCOMPLETE, res2.status());
        assertFalse(res2.isAligned());

        // 3. Response Time arrives at t0 + 5s
        TelemetryAlignmentResult res3 = alignmentService.ingestAndAlign(
            serviceId, "RESPONSE_TIME", 120.0, t0.plusSeconds(5));
        assertEquals(TelemetryAlignmentResult.Status.INCOMPLETE, res3.status());
        assertFalse(res3.isAligned());

        // 4. Error Rate arrives at t0 + 6s -> Complete vector aligned!
        TelemetryAlignmentResult res4 = alignmentService.ingestAndAlign(
            serviceId, "ERROR_RATE", 0.5, t0.plusSeconds(6));
        assertEquals(TelemetryAlignmentResult.Status.ALIGNED, res4.status());
        assertTrue(res4.isAligned());

        MultivariateFeatureVector vec = res4.alignedVector();
        assertNotNull(vec);
        assertEquals(45.0, vec.getCpuUsage());
        assertEquals(60.0, vec.getMemoryUsage());
        assertEquals(120.0, vec.getResponseTime());
        assertEquals(0.5, vec.getErrorRate());
        assertEquals(t0.plusSeconds(6), vec.getRecordedAt()); // alignment timestamp is max event time
    }

    @Test
    void missingDimensions_NeverZeroSubstituted() {
        Long serviceId = 102L;
        Instant t0 = Instant.parse("2026-11-12T12:00:00Z");

        // Only CPU and RT ingested
        alignmentService.ingestAndAlign(serviceId, "CPU_USAGE", 55.0, t0);
        TelemetryAlignmentResult res = alignmentService.ingestAndAlign(
            serviceId, "RESPONSE_TIME", 250.0, t0.plusSeconds(1));

        assertEquals(TelemetryAlignmentResult.Status.INCOMPLETE, res.status());
        assertNull(res.alignedVector());
        assertTrue(res.detailMessage().contains("waiting for dimensions"));
        assertTrue(res.detailMessage().contains("MEMORY_USAGE"));
        assertTrue(res.detailMessage().contains("ERROR_RATE"));
    }

    @Test
    void freshnessBoundaries_CpuResponseError180s_Memory300s() {
        Long serviceId = 103L;
        Instant t0 = Instant.parse("2026-11-12T12:00:00Z");

        // CPU arrives at t0
        alignmentService.ingestAndAlign(serviceId, "CPU_USAGE", 40.0, t0);
        // Memory arrives at t0
        alignmentService.ingestAndAlign(serviceId, "MEMORY_USAGE", 50.0, t0);
        // RT arrives at t0
        alignmentService.ingestAndAlign(serviceId, "RESPONSE_TIME", 100.0, t0);

        // Case A: Error rate arrives at t0 + 179s (CPU age = 179s <= 180s freshness limit)
        TelemetryAlignmentResult resFresh = alignmentService.ingestAndAlign(
            serviceId, "ERROR_RATE", 0.2, t0.plusSeconds(179));
        assertEquals(TelemetryAlignmentResult.Status.ALIGNED, resFresh.status());

        // Case B: CPU was at t0. Error rate arrives at t0 + 185s (CPU age = 185s > 180s freshness limit)
        alignmentService.resetService(serviceId);
        alignmentService.ingestAndAlign(serviceId, "CPU_USAGE", 40.0, t0);
        alignmentService.ingestAndAlign(serviceId, "MEMORY_USAGE", 50.0, t0);
        alignmentService.ingestAndAlign(serviceId, "RESPONSE_TIME", 100.0, t0);

        TelemetryAlignmentResult resStaleCpu = alignmentService.ingestAndAlign(
            serviceId, "ERROR_RATE", 0.2, t0.plusSeconds(185));
        assertEquals(TelemetryAlignmentResult.Status.STALE_DROPPED, resStaleCpu.status());
        assertTrue(resStaleCpu.detailMessage().contains("CPU_USAGE is stale"));

        // Case C: Memory has 300s freshness limit. At t0 + 250s, Memory is fresh while CPU/RT updated at t0 + 200s
        alignmentService.resetService(serviceId);
        alignmentService.ingestAndAlign(serviceId, "MEMORY_USAGE", 50.0, t0); // t0
        alignmentService.ingestAndAlign(serviceId, "CPU_USAGE", 40.0, t0.plusSeconds(200));
        alignmentService.ingestAndAlign(serviceId, "RESPONSE_TIME", 100.0, t0.plusSeconds(200));

        // Memory age at t0 + 250s is 250s <= 300s (Fresh!)
        TelemetryAlignmentResult resMemFresh = alignmentService.ingestAndAlign(
            serviceId, "ERROR_RATE", 0.2, t0.plusSeconds(250));
        assertEquals(TelemetryAlignmentResult.Status.ALIGNED, resMemFresh.status());
    }

    @Test
    void readingBehindWatermark_WithinFreshnessLimit_Accepted() {
        Long serviceId = 104L;
        Instant t0 = Instant.parse("2026-11-12T12:05:00Z"); // Latest event time = 12:05:00

        // Advance buffer to 12:05:00 with CPU reading
        alignmentService.ingestAndAlign(serviceId, "CPU_USAGE", 50.0, t0);

        ServiceTelemetryBuffer buffer = alignmentService.getBuffer(serviceId);
        assertNotNull(buffer);
        assertEquals(t0, buffer.getMaxObservedEventTime());
        assertEquals(t0.minusSeconds(30), buffer.getWatermark()); // Watermark = 12:04:30

        // Concrete Scenario from Authority:
        // Incoming memory reading timestamp = 12:04:00 (t0 - 60s).
        // Memory freshness limit = 300 seconds.
        // Watermark = 12:04:30.
        // Reading is behind watermark (12:04:00 < 12:04:30), but within 300s freshness limit.
        // Invariant: Must NOT be rejected merely because it is behind the watermark!
        Instant tMem = Instant.parse("2026-11-12T12:04:00Z");
        TelemetryAlignmentResult resMem = alignmentService.ingestAndAlign(serviceId, "MEMORY_USAGE", 65.0, tMem);
        assertEquals(TelemetryAlignmentResult.Status.INCOMPLETE, resMem.status());
        assertFalse(resMem.isAligned());

        // Now supply the remaining 2 dimensions:
        // Response time at 12:04:35 (age 25s <= 180s)
        alignmentService.ingestAndAlign(serviceId, "RESPONSE_TIME", 120.0, Instant.parse("2026-11-12T12:04:35Z"));
        // Error rate at 12:04:50 (age 10s <= 180s)
        TelemetryAlignmentResult resFinal = alignmentService.ingestAndAlign(
            serviceId, "ERROR_RATE", 0.3, Instant.parse("2026-11-12T12:04:50Z"));

        // Complete 4D vector successfully aligns at 12:05:00!
        assertEquals(TelemetryAlignmentResult.Status.ALIGNED, resFinal.status());
        assertTrue(resFinal.isAligned());
        MultivariateFeatureVector vector = resFinal.alignedVector();
        assertNotNull(vector);
        assertEquals(50.0, vector.getCpuUsage());
        assertEquals(65.0, vector.getMemoryUsage());
        assertEquals(120.0, vector.getResponseTime());
        assertEquals(0.3, vector.getErrorRate());
        assertEquals(t0, vector.getRecordedAt());
    }

    @Test
    void readingGenuinelyTooLate_ForFinalizedWindow_DroppedAsLate() {
        Long serviceId = 106L;
        Instant t0 = Instant.parse("2026-11-12T12:00:00Z");

        // Step 1: Align all 4 dimensions at t0 (12:00:00)
        alignmentService.ingestAndAlign(serviceId, "CPU_USAGE", 40.0, t0);
        alignmentService.ingestAndAlign(serviceId, "MEMORY_USAGE", 50.0, t0);
        alignmentService.ingestAndAlign(serviceId, "RESPONSE_TIME", 100.0, t0);
        TelemetryAlignmentResult eval1 = alignmentService.ingestAndAlign(serviceId, "ERROR_RATE", 0.1, t0);
        assertTrue(eval1.isAligned());
        assertEquals(t0, alignmentService.getBuffer(serviceId).getLastEvaluatedEventTime());

        // Step 2: Time advances to 12:01:00 (t0 + 60s) -> Watermark advances to 12:00:30 (t0 + 30s)
        alignmentService.ingestAndAlign(serviceId, "CPU_USAGE", 45.0, t0.plusSeconds(60));
        Instant watermark = alignmentService.getBuffer(serviceId).getWatermark();
        assertEquals(t0.plusSeconds(30), watermark); // 12:00:30

        // Because watermark (12:00:30) > lastEvaluatedEventTime (12:00:00), window at 12:00:00 is FINALIZED!
        // Step 3: An incoming reading arrives for Response Time at 12:00:15 (older than watermark and already-evaluated window)
        TelemetryAlignmentResult lateRes = alignmentService.ingestAndAlign(
            serviceId, "RESPONSE_TIME", 110.0, t0.plusSeconds(15));

        // Invariant: Must be dropped as LATE_DROPPED
        assertEquals(TelemetryAlignmentResult.Status.LATE_DROPPED, lateRes.status());
        assertTrue(lateRes.detailMessage().contains("older than slot") || lateRes.detailMessage().contains("older than finalized window"));
    }

    @Test
    void staleReading_OutsideFreshnessLimit_DroppedAsStale() {
        Long serviceId = 107L;
        Instant t0 = Instant.parse("2026-11-12T12:05:00Z");

        // Advance buffer to 12:05:00
        alignmentService.ingestAndAlign(serviceId, "MEMORY_USAGE", 55.0, t0);

        // Incoming CPU reading at 12:01:00 (4 minutes = 240s old)
        // CPU freshness limit is 180 seconds.
        Instant tStale = Instant.parse("2026-11-12T12:01:00Z");
        TelemetryAlignmentResult resStale = alignmentService.ingestAndAlign(serviceId, "CPU_USAGE", 40.0, tStale);

        assertEquals(TelemetryAlignmentResult.Status.STALE_DROPPED, resStale.status());
        assertTrue(resStale.detailMessage().contains("exceeds live freshness limit"));
    }

    @Test
    void sameValueDifferentTimestamp_AcceptedAndUpdatesSlot() {
        Long serviceId = 108L;
        Instant t0 = Instant.parse("2026-11-12T12:00:00Z");

        // First reading
        alignmentService.ingestAndAlign(serviceId, "CPU_USAGE", 50.0, t0);

        // Same value (50.0) at newer timestamp (t0 + 10s)
        TelemetryAlignmentResult res = alignmentService.ingestAndAlign(
            serviceId, "CPU_USAGE", 50.0, t0.plusSeconds(10));

        // Must NOT be discarded as duplicate!
        assertFalse(res.status() == TelemetryAlignmentResult.Status.DUPLICATE_SUPPRESSED);
        assertEquals(TelemetryAlignmentResult.Status.INCOMPLETE, res.status());
        assertEquals(t0.plusSeconds(10), alignmentService.getBuffer(serviceId).getMaxObservedEventTime());
    }

    @Test
    void duplicateArrivals_SuppressedIdempotently() {
        Long serviceId = 105L;
        Instant t0 = Instant.parse("2026-11-12T12:00:00Z");

        // First arrival
        TelemetryAlignmentResult res1 = alignmentService.ingestAndAlign(
            serviceId, "CPU_USAGE", 50.0, t0);
        assertEquals(TelemetryAlignmentResult.Status.INCOMPLETE, res1.status());

        // Exact duplicate arrival (same timestamp, same value)
        TelemetryAlignmentResult resDup = alignmentService.ingestAndAlign(
            serviceId, "CPU_USAGE", 50.0, t0);
        assertEquals(TelemetryAlignmentResult.Status.DUPLICATE_SUPPRESSED, resDup.status());
        assertTrue(resDup.detailMessage().contains("Duplicate reading suppressed"));
    }

    @Test
    void multipleServices_CompletelyIsolated() {
        Long s1 = 201L;
        Long s2 = 202L;
        Instant t0 = Instant.parse("2026-11-12T12:00:00Z");

        // Service 1 gets CPU and MEM
        alignmentService.ingestAndAlign(s1, "CPU_USAGE", 30.0, t0);
        alignmentService.ingestAndAlign(s1, "MEMORY_USAGE", 40.0, t0);

        // Service 2 gets RT and ERR
        alignmentService.ingestAndAlign(s2, "RESPONSE_TIME", 90.0, t0);
        alignmentService.ingestAndAlign(s2, "ERROR_RATE", 0.1, t0);

        // Neither should be complete
        assertFalse(alignmentService.getBuffer(s1).getWatermark() == null);
        assertFalse(alignmentService.getBuffer(s2).getWatermark() == null);
        assertEquals(2, alignmentService.getActiveBufferCount());

        // Now complete service 1
        alignmentService.ingestAndAlign(s1, "RESPONSE_TIME", 100.0, t0);
        TelemetryAlignmentResult res1 = alignmentService.ingestAndAlign(s1, "ERROR_RATE", 0.05, t0);
        assertTrue(res1.isAligned());
        assertEquals(30.0, res1.alignedVector().getCpuUsage());

        // Service 2 is still incomplete
        TelemetryAlignmentResult res2 = alignmentService.ingestAndAlign(s2, "CPU_USAGE", 80.0, t0);
        assertEquals(TelemetryAlignmentResult.Status.INCOMPLETE, res2.status());
    }

    @Test
    void concurrentUpdates_ThreadSafeAlignment() throws Exception {
        Long serviceId = 301L;
        Instant t0 = Instant.parse("2026-11-12T12:00:00Z");

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<TelemetryAlignmentResult> results = new ArrayList<>();

        executor.submit(() -> {
            try {
                latch.await();
                TelemetryAlignmentResult r = alignmentService.ingestAndAlign(serviceId, "CPU_USAGE", 45.0, t0);
                synchronized (results) { results.add(r); }
            } catch (Exception ignored) {} finally { doneLatch.countDown(); }
        });
        executor.submit(() -> {
            try {
                latch.await();
                TelemetryAlignmentResult r = alignmentService.ingestAndAlign(serviceId, "MEMORY_USAGE", 60.0, t0.plusMillis(10));
                synchronized (results) { results.add(r); }
            } catch (Exception ignored) {} finally { doneLatch.countDown(); }
        });
        executor.submit(() -> {
            try {
                latch.await();
                TelemetryAlignmentResult r = alignmentService.ingestAndAlign(serviceId, "RESPONSE_TIME", 120.0, t0.plusMillis(20));
                synchronized (results) { results.add(r); }
            } catch (Exception ignored) {} finally { doneLatch.countDown(); }
        });
        executor.submit(() -> {
            try {
                latch.await();
                TelemetryAlignmentResult r = alignmentService.ingestAndAlign(serviceId, "ERROR_RATE", 0.5, t0.plusMillis(30));
                synchronized (results) { results.add(r); }
            } catch (Exception ignored) {} finally { doneLatch.countDown(); }
        });

        latch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // Exactly one thread must have produced an ALIGNED result (the last one to complete the 4 dimensions)
        long alignedCount = results.stream().filter(TelemetryAlignmentResult::isAligned).count();
        assertEquals(1, alignedCount);

        MultivariateFeatureVector vector = results.stream()
            .filter(TelemetryAlignmentResult::isAligned)
            .findFirst()
            .get()
            .alignedVector();
        assertNotNull(vector);
        assertEquals(45.0, vector.getCpuUsage());
        assertEquals(60.0, vector.getMemoryUsage());
        assertEquals(120.0, vector.getResponseTime());
        assertEquals(0.5, vector.getErrorRate());
    }

    @Test
    void recoveryAndReset_RestartsCleanly() {
        Long serviceId = 401L;
        Instant t0 = Instant.parse("2026-11-12T12:00:00Z");

        alignmentService.ingestAndAlign(serviceId, "CPU_USAGE", 50.0, t0);
        alignmentService.ingestAndAlign(serviceId, "MEMORY_USAGE", 60.0, t0);

        // Simulate service buffer reset (restart/recovery)
        alignmentService.resetService(serviceId);
        ServiceTelemetryBuffer buffer = alignmentService.getBuffer(serviceId);
        assertNull(buffer.getMaxObservedEventTime());
        assertNull(buffer.getWatermark());

        // Re-ingest fresh metrics
        alignmentService.ingestAndAlign(serviceId, "CPU_USAGE", 52.0, t0.plusSeconds(10));
        alignmentService.ingestAndAlign(serviceId, "MEMORY_USAGE", 62.0, t0.plusSeconds(10));
        alignmentService.ingestAndAlign(serviceId, "RESPONSE_TIME", 110.0, t0.plusSeconds(10));
        TelemetryAlignmentResult res = alignmentService.ingestAndAlign(serviceId, "ERROR_RATE", 0.4, t0.plusSeconds(10));

        assertTrue(res.isAligned());
        assertEquals(52.0, res.alignedVector().getCpuUsage());
    }
}
