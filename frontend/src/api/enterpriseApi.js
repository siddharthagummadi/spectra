import { request } from "./apiClient";

export async function getMyEnterpriseAgents() {
  return request("/api/enterprise/agents");
}
