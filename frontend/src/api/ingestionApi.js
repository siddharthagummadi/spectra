import { apiClient } from "./apiClient";

export async function previewDataset(file) {
  const formData = new FormData();
  formData.append("file", file);

  const res = await apiClient.post("/api/ingestion/preview", formData);
  return res.data;
}

export async function previewDatasetText(csvText, filename = "dataset.csv") {
  const res = await apiClient.post("/api/ingestion/preview-text", {
    csvContent: csvText,
    filename: filename
  });
  return res.data;
}

export async function confirmIngestion(payload) {
  // payload: { batchKey, filename, records, groundTruthIncluded }
  const res = await apiClient.post("/api/ingestion/confirm", payload);
  return res.data;
}

export async function getBatches() {
  const res = await apiClient.get("/api/ingestion/batches");
  return res.data;
}

export async function deleteBatch(batchId) {
  const res = await apiClient.delete(`/api/ingestion/batches/${batchId}`);
  return res.data;
}

export async function downloadTemplateCsv() {
  const res = await apiClient.get("/api/ingestion/template", {
    responseType: "text"
  });
  return res.data;
}

export async function submitManualTelemetry(payload) {
  // payload: { serviceName, metricName, metricValue, unit, timestamp, environment, scenarioLabel, groundTruthLabel }
  const res = await apiClient.post("/api/ingestion/manual", payload);
  return res.data;
}
