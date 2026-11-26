import { request } from "./apiClient";

export async function getPlatformOverview() {
  return request("/api/admin/overview");
}

export async function getAllEnterprises() {
  return request("/api/admin/enterprises");
}

export async function createEnterprise(payload) {
  return request("/api/admin/enterprises", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function getEnterpriseDashboard(enterpriseId) {
  return request(`/api/admin/enterprises/${enterpriseId}`);
}

export async function updateEnterpriseStatus(enterpriseId, status, reason = "") {
  return request(`/api/admin/enterprises/${enterpriseId}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status, reason })
  });
}

export async function generateEnrollmentToken(enterpriseId, ttlMinutes = 60) {
  return request(`/api/admin/enterprises/${enterpriseId}/enrollment-token?ttlMinutes=${ttlMinutes}`, {
    method: "POST"
  });
}

export async function getAllAgents() {
  return request("/api/admin/agents");
}

export async function rotateAgentCredential(agentId) {
  return request(`/api/admin/agents/${agentId}/credentials/rotate`, {
    method: "POST"
  });
}

export async function deactivateAgent(agentId) {
  return request(`/api/admin/agents/${agentId}/deactivate`, {
    method: "POST"
  });
}

export async function getAuditLogs(limit = 50) {
  return request(`/api/audit-logs?limit=${limit}`);
}
