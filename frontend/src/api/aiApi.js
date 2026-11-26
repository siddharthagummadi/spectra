import { request } from "./apiClient";

export async function getAiHealth() {
  return request("/api/ai/health");
}

export async function analyzeRootCause(payload) {
  return request("/api/ai/rca", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function getRunbooks(query, topK = 3, metricFilter = null) {
  return request("/api/ai/runbooks", {
    method: "POST",
    body: JSON.stringify({ query, topK, metricFilter })
  });
}

export async function forecastTelemetry(payload) {
  return request("/api/ai/forecast", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function askCopilot(message, operationalContext = {}) {
  return request("/api/ai/copilot", {
    method: "POST",
    body: JSON.stringify({ message, operationalContext })
  });
}

export async function getCopilotConfig() {
  return request("/api/ai/copilot/config");
}

export async function updateCopilotConfig(payload) {
  return request("/api/ai/copilot/config", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}
