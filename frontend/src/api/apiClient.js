const BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

let activeCredentials = null;
let unauthorizedHandler = null;

export function setApiCredentials(credentials) {
  activeCredentials = credentials;
}

export function getApiCredentials() {
  return activeCredentials;
}

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler;
}

export async function request(endpoint, options = {}) {
  const url = `${BASE_URL}${endpoint}`;
  const headers = {
    "Content-Type": "application/json",
    ...options.headers
  };

  const creds = activeCredentials;
  if (creds && !headers["Authorization"]) {
    headers["Authorization"] = `Basic ${creds}`;
  }

  const config = {
    ...options,
    headers
  };

  let response;
  try {
    response = await fetch(url, config);
  } catch (err) {
    const error = new Error("Unable to connect to Spectra server. Please check your network connection.");
    error.status = 0;
    throw error;
  }

  if (!response.ok) {
    let errorData = null;
    try {
      errorData = await response.json();
    } catch {
      errorData = null;
    }

    if (response.status === 401 && unauthorizedHandler) {
      unauthorizedHandler();
    }

    if (response.status === 403 && (!errorData || !errorData.message)) {
      errorData = { message: "Access denied: You do not have permission to perform this administrative action." };
    }

    const errorMessage = errorData?.message || `Request failed with status ${response.status}`;
    const error = new Error(errorMessage);
    error.status = response.status;
    error.fieldErrors = errorData?.fieldErrors || null;
    error.data = errorData;
    throw error;
  }

  if (response.status === 204) {
    return null;
  }

  const contentType = response.headers.get("content-type");
  if (contentType && contentType.includes("application/json")) {
    return await response.json();
  }

  return null;
}
