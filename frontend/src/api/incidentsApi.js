import { request } from "./apiClient";

export async function getAllIncidents(status, serviceId) {
  const params = new URLSearchParams();
  if (status) params.append("status", status);
  if (serviceId) params.append("serviceId", serviceId);
  const query = params.toString() ? `?${params.toString()}` : "";
  return request(`/api/incidents${query}`);
}

export async function getIncidentsByServiceId(serviceId) {
  return request(`/api/incidents/service/${serviceId}`);
}

export async function getIncidentById(id) {
  return request(`/api/incidents/${id}`);
}

export async function createIncident(payload) {
  return request("/api/incidents", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function transitionStatus(id, payload) {
  return request(`/api/incidents/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify(payload)
  });
}

export async function getNotes(incidentId) {
  return request(`/api/incidents/${incidentId}/notes`);
}

export async function addNote(incidentId, payload) {
  return request(`/api/incidents/${incidentId}/notes`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}
