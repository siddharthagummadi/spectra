package com.spectra.agent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Entrypoint for the standalone Spectra Enterprise Monitoring Agent.
 */
public class SpectraAgentMain {

    private static final String STATE_FILE = "agent-state.properties";

    public static void main(String[] args) throws Exception {
        AgentConfig config = AgentConfig.fromArgs(args);
        Files.createDirectories(config.getStateDir());

        SpectraApiClient client = new SpectraApiClient(config.getServerUrl());
        LocalBufferStore buffer = new LocalBufferStore(config.getStateDir(), 500);
        TelemetryCollector collector = new TelemetryCollector();

        String credential = ensureEnrolled(config, client);
        System.out.println("[Spectra-Agent] Authenticated for agentCode=" + config.getAgentCode()
                + " -> server=" + config.getServerUrl());

        AtomicLong collectionFailures = new AtomicLong(0);
        AtomicLong transmissionFailures = new AtomicLong(0);
        AtomicReference<Instant> lastSuccessfulSync = new AtomicReference<>(null);
        AtomicReference<String> lastError = new AtomicReference<>(null);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        Runnable collectAndFlushTask = () -> {
            try {
                List<AgentModels.TelemetrySample> samples = collector.collect(config);
                if (!samples.isEmpty()) {
                    buffer.enqueue(new AgentModels.TelemetryBatch(UUID.randomUUID().toString(), samples));
                }
            } catch (Exception ex) {
                collectionFailures.incrementAndGet();
                lastError.set("Collection error: " + ex.getMessage());
            }

            flushWithBackoff(client, buffer, credential, transmissionFailures, lastSuccessfulSync, lastError);
        };

        Runnable heartbeatTask = () -> {
            AgentModels.HeartbeatRequest hb = new AgentModels.HeartbeatRequest(
                    config.getAgentVersion(),
                    buffer.size() > 0 ? "SYNC_DELAYED" : "ONLINE",
                    buffer.size(),
                    collectionFailures.get(),
                    transmissionFailures.get(),
                    lastSuccessfulSync.get(),
                    lastError.get()
            );
            boolean ok = client.sendHeartbeat(credential, hb);
            if (!ok) {
                transmissionFailures.incrementAndGet();
            }
        };

        scheduler.scheduleAtFixedRate(collectAndFlushTask, 0, config.getCollectionIntervalSec(), TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(heartbeatTask, 2, config.getHeartbeatIntervalSec(), TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Spectra-Agent] Shutting down...");
            scheduler.shutdownNow();
        }));
    }

    private static void flushWithBackoff(
            SpectraApiClient client,
            LocalBufferStore buffer,
            String credential,
            AtomicLong transmissionFailures,
            AtomicReference<Instant> lastSuccessfulSync,
            AtomicReference<String> lastError) {
        try {
            List<AgentModels.TelemetryBatch> pending = buffer.readAll();
            for (AgentModels.TelemetryBatch batch : pending) {
                boolean sent = false;
                for (int attempt = 0; attempt < 3; attempt++) {
                    SpectraApiClient.SendResult res = client.sendBatch(credential, batch);
                    if (res.success()) {
                        buffer.removeByBatchId(batch.clientBatchId());
                        lastSuccessfulSync.set(Instant.now());
                        lastError.set(null);
                        sent = true;
                        break;
                    }
                    transmissionFailures.incrementAndGet();
                    lastError.set("HTTP " + res.statusCode() + ": " + res.message());
                    if (!res.retryable()) {
                        buffer.removeByBatchId(batch.clientBatchId()); // drop malformed 4xx batch
                        sent = true;
                        break;
                    }
                    Thread.sleep(SpectraApiClient.computeBackoffMs(attempt));
                }
                if (!sent) {
                    break; // Keep remaining batches safely buffered on disk
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            transmissionFailures.incrementAndGet();
            lastError.set("Flush error: " + ex.getMessage());
        }
    }

    private static String ensureEnrolled(AgentConfig config, SpectraApiClient client) throws Exception {
        Path statePath = config.getStateDir().resolve(STATE_FILE);
        Properties props = new Properties();
        if (Files.exists(statePath)) {
            try (InputStream in = Files.newInputStream(statePath)) {
                props.load(in);
            }
            String existingCred = props.getProperty("agentCredential");
            if (existingCred != null && !existingCred.isBlank()) {
                return existingCred.trim();
            }
        }

        if (config.getEnrollToken() == null || config.getEnrollToken().isBlank()) {
            throw new IllegalStateException("No persisted agent credential found and --enroll-token not provided.");
        }

        String hostname = InetAddress.getLocalHost().getHostName();
        String osInfo = System.getProperty("os.name") + " " + System.getProperty("os.version");
        AgentModels.EnrollResponse res = client.enroll(new AgentModels.EnrollRequest(
                config.getEnrollToken(),
                config.getAgentCode(),
                hostname,
                osInfo,
                config.getAgentVersion()
        ));

        props.setProperty("agentId", String.valueOf(res.agentId()));
        props.setProperty("enterpriseId", String.valueOf(res.enterpriseId()));
        props.setProperty("agentCode", res.agentCode());
        props.setProperty("agentCredential", res.agentCredential());
        try (OutputStream out = Files.newOutputStream(statePath)) {
            props.store(out, "Spectra Enterprise Monitoring Agent State");
        }
        return res.agentCredential();
    }
}
