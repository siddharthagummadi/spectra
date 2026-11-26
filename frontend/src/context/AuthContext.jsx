import React, { createContext, useContext, useState, useEffect, useCallback } from "react";
import { setApiCredentials, setUnauthorizedHandler } from "../api/apiClient";

const STORAGE_KEY_CREDS = "spectra_auth_credentials";
const STORAGE_KEY_USER = "spectra_auth_user";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  // Synchronously initialize from storage to prevent flashing login page on refresh
  const [credentials, setCredentials] = useState(() => {
    try {
      return localStorage.getItem(STORAGE_KEY_CREDS) || sessionStorage.getItem(STORAGE_KEY_CREDS) || null;
    } catch {
      return null;
    }
  });

  const [user, setUser] = useState(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY_USER) || sessionStorage.getItem(STORAGE_KEY_USER);
      return stored ? JSON.parse(stored) : null;
    } catch {
      return null;
    }
  });

  const [isInitializing, setIsInitializing] = useState(false);

  // Configure apiClient immediately when credentials change
  useEffect(() => {
    setApiCredentials(credentials);
  }, [credentials]);

  // Handle 401 Unauthorized globally
  useEffect(() => {
    setUnauthorizedHandler(() => {
      setCredentials(null);
      setUser(null);
      try {
        localStorage.removeItem(STORAGE_KEY_CREDS);
        localStorage.removeItem(STORAGE_KEY_USER);
        sessionStorage.removeItem(STORAGE_KEY_CREDS);
        sessionStorage.removeItem(STORAGE_KEY_USER);
      } catch {}
    });
  }, []);

  const login = useCallback((userData, creds) => {
    setCredentials(creds);
    const parsedUser = typeof userData === "string" ? { email: userData, role: "USER" } : userData;
    setUser(parsedUser);
    setApiCredentials(creds);

    try {
      localStorage.setItem(STORAGE_KEY_CREDS, creds);
      localStorage.setItem(STORAGE_KEY_USER, JSON.stringify(parsedUser));
      sessionStorage.setItem(STORAGE_KEY_CREDS, creds);
      sessionStorage.setItem(STORAGE_KEY_USER, JSON.stringify(parsedUser));
    } catch (e) {
      console.warn("Failed to persist auth session to storage:", e);
    }
  }, []);

  const logout = useCallback(() => {
    setCredentials(null);
    setUser(null);
    setApiCredentials(null);

    try {
      localStorage.removeItem(STORAGE_KEY_CREDS);
      localStorage.removeItem(STORAGE_KEY_USER);
      sessionStorage.removeItem(STORAGE_KEY_CREDS);
      sessionStorage.removeItem(STORAGE_KEY_USER);
    } catch {}
  }, []);

  const value = {
    credentials,
    user,
    role: user?.role || "USER",
    isAdmin: user?.role === "ADMIN",
    isAuthenticated: Boolean(credentials && user),
    isInitializing,
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
    user: null,
    role: "USER",
    isAdmin: false,
    isAuthenticated: false,
    isInitializing: false,
    login: () => {},
    logout: () => {}
  };
}
