import { apiClient } from "./apiClient";

export async function runEvaluation(payload) {
  // payload: { batchId, datasetName, algorithm, scenarioFilter }
  const res = await apiClient.post("/api/evaluation/run", payload);
  return res.data;
}

export async function getExperiments() {
  const res = await apiClient.get("/api/evaluation/experiments");
  return res.data;
}

export async function downloadExperimentCsv(id) {
  const res = await apiClient.get(`/api/evaluation/experiments/${id}/csv`, {
    responseType: "text"
  });
  return res.data;
}
