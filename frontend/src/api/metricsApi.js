import { request } from "./apiClient";

export async function submitMetric(payload) {
  return request("/api/metrics", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function getRecentMetrics(limit = 50) {
  return request(`/api/metrics/recent?limit=${limit}`);
}

export async function getMetricsByServiceId(serviceId, limit = 50) {
  return request(`/api/metrics/service/${serviceId}?limit=${limit}`);
}
