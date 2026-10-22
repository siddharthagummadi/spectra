import { request } from "./apiClient";

export async function registerUser(payload) {
  return request("/api/users/register", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function verifyCredentials(email, password) {
  const credentials = btoa(`${email}:${password}`);
  const baseUrl = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
  const response = await fetch(`${baseUrl}/api/users/me`, {
    headers: {
      Authorization: `Basic ${credentials}`
    }
  });

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error("Invalid email or password");
    }
    throw new Error(`Authentication check failed with status ${response.status}`);
  }

  const user = await response.json();
  return {
    email: user.email,
    fullName: user.fullName,
    role: user.role,
    credentials
  };
}

export async function checkBackendHealth() {
  return request("/api/health");
}
