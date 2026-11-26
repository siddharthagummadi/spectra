import { request } from "./apiClient";

export async function quickLaunchDemo(scenario = null) {
  const query = scenario ? `?scenario=${encodeURIComponent(scenario)}` : "";
  return request(`/api/demo/quick-launch${query}`, {
    method: "POST"
  });
}

export async function seedDemoData(scenario = null) {
  const query = scenario ? `?scenario=${encodeURIComponent(scenario)}` : "";
  return request(`/api/demo/seed${query}`, {
    method: "POST"
  });
}

export async function resetDemoData() {
  return request("/api/demo/reset", {
    method: "POST"
  });
}

export async function tickDemoData() {
  return request("/api/demo/tick", {
    method: "POST"
  });
}

export async function getDemoStatus() {
  return request("/api/demo/status");
}
