import { request } from "./apiClient";

export async function getAllServices() {
  return request("/api/services");
}

export async function getServiceById(id) {
  return request(`/api/services/${id}`);
}

export async function createService(payload) {
  return request("/api/services", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function updateService(id, payload) {
  return request(`/api/services/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

export async function updateServiceStatus(id, active) {
  return request(`/api/services/${id}/status?active=${active}`, {
    method: "PATCH"
  });
}
