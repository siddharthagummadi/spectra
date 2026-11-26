package com.spectra.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Bounded persistent disk buffer (JSONL) preserving unsent telemetry batches
 * during Spectra server downtime or enterprise network interruptions.
 */
public class LocalBufferStore {

    private final Path bufferFile;
    private final int maxBatches;
    private final ObjectMapper mapper;

    public LocalBufferStore(Path stateDir, int maxBatches) throws IOException {
        Files.createDirectories(stateDir);
        this.bufferFile = stateDir.resolve("telemetry-buffer.jsonl");
        this.maxBatches = Math.max(10, maxBatches);
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public synchronized void enqueue(AgentModels.TelemetryBatch batch) throws IOException {
        List<AgentModels.TelemetryBatch> existing = readAll();
        existing.add(batch);
        while (existing.size() > maxBatches) {
            existing.remove(0); // drop oldest when capacity reached
        }
        writeAll(existing);
    }

    public synchronized List<AgentModels.TelemetryBatch> readAll() throws IOException {
        if (!Files.exists(bufferFile)) {
            return new ArrayList<>();
        }
        List<String> lines = Files.readAllLines(bufferFile, StandardCharsets.UTF_8);
        List<AgentModels.TelemetryBatch> result = new ArrayList<>();
        for (String line : lines) {
            if (!line.isBlank()) {
                result.add(mapper.readValue(line, AgentModels.TelemetryBatch.class));
            }
        }
        return result;
    }

    public synchronized void removeByBatchId(String clientBatchId) throws IOException {
        List<AgentModels.TelemetryBatch> remaining = readAll().stream()
                .filter(b -> !clientBatchId.equals(b.clientBatchId()))
                .toList();
        writeAll(remaining);
    }

    public synchronized int size() {
        try {
            return readAll().size();
        } catch (IOException e) {
            return 0;
        }
    }

    private void writeAll(List<AgentModels.TelemetryBatch> batches) throws IOException {
        List<String> lines = new ArrayList<>(batches.size());
        for (AgentModels.TelemetryBatch b : batches) {
            lines.add(mapper.writeValueAsString(b));
        }
        Files.write(bufferFile, lines, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
