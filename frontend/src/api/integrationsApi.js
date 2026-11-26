import { apiClient } from "./apiClient";

export async function createIntegrationRequest(payload) {
  // payload: { enterpriseName, organizationName, environment, integrationType, apiEndpoint, contactName, contactEmail, connectionPurpose, credentials }
  const res = await apiClient.post("/api/integrations/request", payload);
  return res.data;
}

export async function getIntegrations() {
  const res = await apiClient.get("/api/integrations");
  return res.data;
}

export async function updateIntegrationStatus(id, payload) {
  // payload: { status, reason }
  const res = await apiClient.put(`/api/integrations/${id}/status`, payload);
  return res.data;
}
