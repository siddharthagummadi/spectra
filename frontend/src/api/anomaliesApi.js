import { request } from "./apiClient";

export async function getRecentAnomalies(limit = 50) {
  return request(`/api/anomalies/recent?limit=${limit}`);
}

export async function getAnomaliesByServiceId(serviceId, limit = 50) {
  return request(`/api/anomalies/service/${serviceId}?limit=${limit}`);
}
