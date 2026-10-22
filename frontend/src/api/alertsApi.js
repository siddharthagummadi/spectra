import { request } from "./apiClient";

export async function getAllRules() {
  return request("/api/alert-rules");
}

export async function getRulesByServiceId(serviceId) {
  return request(`/api/alert-rules/service/${serviceId}`);
}

export async function getRuleById(id) {
  return request(`/api/alert-rules/${id}`);
}

export async function createRule(payload) {
  return request("/api/alert-rules", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function updateRule(id, payload) {
  return request(`/api/alert-rules/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

export async function updateRuleStatus(id, active) {
  return request(`/api/alert-rules/${id}/status?active=${active}`, {
    method: "PATCH"
  });
}

export async function deleteRule(id) {
  return request(`/api/alert-rules/${id}`, {
    method: "DELETE"
  });
}

export async function getAllAlerts(status) {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  return request(`/api/alerts${query}`);
}

export async function getAlertsByServiceId(serviceId, status) {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  return request(`/api/alerts/service/${serviceId}${query}`);
}

export async function getAlertById(id) {
  return request(`/api/alerts/${id}`);
}

export async function acknowledgeAlert(id) {
  return request(`/api/alerts/${id}/acknowledge`, {
    method: "PATCH"
  });
}
