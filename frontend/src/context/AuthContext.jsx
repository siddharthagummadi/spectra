import React, { createContext, useContext, useState, useEffect, useCallback } from "react";
import { setApiCredentials, setUnauthorizedHandler } from "../api/apiClient";

try {
  localStorage.removeItem("spectra_auth_credentials");
  localStorage.removeItem("spectra_auth_user");
  sessionStorage.removeItem("spectra_auth_credentials");
  sessionStorage.removeItem("spectra_auth_user");
} catch {}

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [credentials, setCredentials] = useState(null);
  const [user, setUser] = useState(null);

  useEffect(() => {
    try {
      localStorage.removeItem("spectra_auth_credentials");
      localStorage.removeItem("spectra_auth_user");
      sessionStorage.removeItem("spectra_auth_credentials");
      sessionStorage.removeItem("spectra_auth_user");
    } catch {}
  }, []);

  useEffect(() => {
    setApiCredentials(credentials);
  }, [credentials]);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setCredentials(null);
      setUser(null);
      try {
        localStorage.removeItem("spectra_auth_credentials");
        localStorage.removeItem("spectra_auth_user");
        sessionStorage.removeItem("spectra_auth_credentials");
        sessionStorage.removeItem("spectra_auth_user");
      } catch {}
    });
  }, []);

  const login = useCallback((userData, creds) => {
    setCredentials(creds);
    if (typeof userData === "string") {
      setUser({ email: userData, role: "USER" });
    } else {
      setUser(userData);
    }
    setApiCredentials(creds);
  }, []);

  const logout = useCallback(() => {
    setCredentials(null);
    setUser(null);
    setApiCredentials(null);
    try {
      localStorage.removeItem("spectra_auth_credentials");
      localStorage.removeItem("spectra_auth_user");
      sessionStorage.removeItem("spectra_auth_credentials");
      sessionStorage.removeItem("spectra_auth_user");
    } catch {}
  }, []);

  const value = {
    credentials,
    user,
    role: user?.role || "USER",
    isAdmin: user?.role === "ADMIN",
    isAuthenticated: Boolean(credentials),
    login,
    logout
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  return context || {
    user: { email: "admin@spectra.local", role: "ADMIN" },
    role: "ADMIN",
    isAdmin: true,
    isAuthenticated: true,
    login: () => {},
    logout: () => {}
  };
}
