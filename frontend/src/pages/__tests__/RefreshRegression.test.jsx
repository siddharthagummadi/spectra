import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent, act } from "@testing-library/react";
import { MemoryRouter, Routes, Route, Navigate } from "react-router-dom";
import { DashboardPage } from "../DashboardPage";
import { ServicesPage } from "../ServicesPage";
import { Header } from "../../components/layout/Header";
import { AuthProvider, useAuth } from "../../context/AuthContext";
import * as servicesApi from "../../api/servicesApi";
import * as alertsApi from "../../api/alertsApi";
import * as incidentsApi from "../../api/incidentsApi";
import * as metricsApi from "../../api/metricsApi";
import * as usersApi from "../../api/usersApi";
import * as apiClient from "../../api/apiClient";

function ProtectedWrapper({ role = "USER", email = "technova@spectra.local", children }) {
  const { login, isAuthenticated } = useAuth();
  const [initialized, setInitialized] = React.useState(false);

  React.useEffect(() => {
    login({ email, role }, "bW9jay1hdXRoLWNyZWRlbnRpYWxz");
    setInitialized(true);
  }, [email, role, login]);

  if (!initialized) {
    return <div>Loading session...</div>;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div>
      <Header title="Spectra Operations Platform" />
      {children}
    </div>
  );
}

describe("Refresh and Authentication Regression Tests", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(usersApi, "checkBackendHealth").mockResolvedValue({ status: "UP" });
    vi.spyOn(servicesApi, "getAllServices").mockResolvedValue([
      { id: 1, name: "Alpha Service", active: true, environment: "PRODUCTION", ownerEmail: "technova@spectra.local" },
      { id: 2, name: "Beta Service", active: false, environment: "DEVELOPMENT", ownerEmail: "operator@spectra.local" }
    ]);
    vi.spyOn(alertsApi, "getAllRules").mockResolvedValue([
      { id: 10, ruleName: "CPU Rule", active: true, serviceName: "Alpha Service" }
    ]);
    vi.spyOn(alertsApi, "getAllAlerts").mockResolvedValue([
      { id: 20, ruleName: "CPU Rule", serviceName: "Alpha Service", severity: "HIGH", status: "TRIGGERED", triggeredAt: "2026-10-21T07:00:00Z" }
    ]);
    vi.spyOn(incidentsApi, "getAllIncidents").mockResolvedValue([]);
    vi.spyOn(metricsApi, "getRecentMetrics").mockResolvedValue([]);
  });

  it("1. An authenticated USER can click Refresh without being redirected to Login", async () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/dashboard"]}>
          <Routes>
            <Route path="/login" element={<div>LOGIN_PAGE_SHOWN</div>} />
            <Route
              path="/dashboard"
              element={
                <ProtectedWrapper role="USER" email="technova@spectra.local">
                  <DashboardPage />
                </ProtectedWrapper>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("Operations Dashboard")).toBeInTheDocument();
    });

    const refreshButtons = screen.getAllByRole("button", { name: /Refresh/i });
    expect(refreshButtons.length).toBeGreaterThan(0);

    await act(async () => {
      fireEvent.click(refreshButtons[0]);
    });

    await waitFor(() => {
      expect(screen.queryByText("LOGIN_PAGE_SHOWN")).not.toBeInTheDocument();
      expect(screen.getByText("Operations Dashboard")).toBeInTheDocument();
      expect(screen.getAllByText("technova@spectra.local").length).toBeGreaterThan(0);
    });
  });

  it("2. An authenticated ADMIN can click Refresh without being redirected to Login", async () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/dashboard"]}>
          <Routes>
            <Route path="/login" element={<div>LOGIN_PAGE_SHOWN</div>} />
            <Route
              path="/dashboard"
              element={
                <ProtectedWrapper role="ADMIN" email="operator@spectra.local">
                  <DashboardPage />
                </ProtectedWrapper>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("Centralized Admin View")).toBeInTheDocument();
    });

    const refreshButtons = screen.getAllByRole("button", { name: /Refresh/i });
    expect(refreshButtons.length).toBeGreaterThan(0);

    await act(async () => {
      fireEvent.click(refreshButtons[0]);
    });

    await waitFor(() => {
      expect(screen.queryByText("LOGIN_PAGE_SHOWN")).not.toBeInTheDocument();
      expect(screen.getByText("Centralized Admin View")).toBeInTheDocument();
      expect(screen.getAllByText("operator@spectra.local").length).toBeGreaterThan(0);
    });
  });

  it("3. Dashboard data is fetched again after clicking Refresh", async () => {
    const servicesSpy = vi.spyOn(servicesApi, "getAllServices");

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/dashboard"]}>
          <ProtectedWrapper role="ADMIN" email="operator@spectra.local">
            <DashboardPage />
          </ProtectedWrapper>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(servicesSpy).toHaveBeenCalledTimes(1);
    });

    const refreshButtons = screen.getAllByRole("button", { name: /Refresh/i });
    await act(async () => {
      fireEvent.click(refreshButtons[0]);
    });

    await waitFor(() => {
      expect(servicesSpy).toHaveBeenCalledTimes(2);
    });
  });

  it("4. Authentication state and the current route remain intact after a successful refresh", async () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/services"]}>
          <Routes>
            <Route path="/login" element={<div>LOGIN_PAGE_SHOWN</div>} />
            <Route
              path="/services"
              element={
                <ProtectedWrapper role="USER" email="technova@spectra.local">
                  <ServicesPage />
                </ProtectedWrapper>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("Alpha Service")).toBeInTheDocument();
    });

    const refreshButtons = screen.getAllByRole("button", { name: /Refresh/i });
    await act(async () => {
      fireEvent.click(refreshButtons[0]);
    });

    await waitFor(() => {
      expect(screen.queryByText("LOGIN_PAGE_SHOWN")).not.toBeInTheDocument();
      expect(screen.getByText("Alpha Service")).toBeInTheDocument();
      expect(screen.getAllByText("technova@spectra.local").length).toBeGreaterThan(0);
    });
  });

  it("5. A network error does not automatically log out the user", async () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/services"]}>
          <Routes>
            <Route path="/login" element={<div>LOGIN_PAGE_SHOWN</div>} />
            <Route
              path="/services"
              element={
                <ProtectedWrapper role="USER" email="technova@spectra.local">
                  <ServicesPage />
                </ProtectedWrapper>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("Alpha Service")).toBeInTheDocument();
    });

    vi.spyOn(servicesApi, "getAllServices").mockRejectedValue(new Error("Unable to connect to Spectra server."));

    const refreshButtons = screen.getAllByRole("button", { name: /Refresh/i });
    await act(async () => {
      fireEvent.click(refreshButtons[0]);
    });

    await waitFor(() => {
      expect(screen.queryByText("LOGIN_PAGE_SHOWN")).not.toBeInTheDocument();
      expect(screen.getByText(/Unable to connect to Spectra server/i)).toBeInTheDocument();
      expect(screen.getAllByText("technova@spectra.local").length).toBeGreaterThan(0);
    });
  });

  it("6. A 403 response does not incorrectly trigger a login redirect", async () => {
    const error403 = new Error("Access denied: You do not have permission to perform this administrative action.");
    error403.status = 403;

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/services"]}>
          <Routes>
            <Route path="/login" element={<div>LOGIN_PAGE_SHOWN</div>} />
            <Route
              path="/services"
              element={
                <ProtectedWrapper role="USER" email="technova@spectra.local">
                  <ServicesPage />
                </ProtectedWrapper>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("Alpha Service")).toBeInTheDocument();
    });

    vi.spyOn(servicesApi, "getAllServices").mockRejectedValue(error403);

    const refreshButtons = screen.getAllByRole("button", { name: /Refresh/i });
    await act(async () => {
      fireEvent.click(refreshButtons[0]);
    });

    await waitFor(() => {
      expect(screen.queryByText("LOGIN_PAGE_SHOWN")).not.toBeInTheDocument();
      expect(screen.getByText(/Access denied: You do not have permission/i)).toBeInTheDocument();
      expect(screen.getAllByText("technova@spectra.local").length).toBeGreaterThan(0);
    });
  });

  it("7. A genuine authentication failure is handled according to the application's established security policy", async () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/services"]}>
          <Routes>
            <Route path="/login" element={<div>LOGIN_PAGE_SHOWN</div>} />
            <Route
              path="/services"
              element={
                <ProtectedWrapper role="USER" email="technova@spectra.local">
                  <ServicesPage />
                </ProtectedWrapper>
              }
            />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("Alpha Service")).toBeInTheDocument();
    });

    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      headers: new Headers({ "content-type": "application/json" }),
      json: async () => ({ message: "Unauthorized" })
    });

    vi.spyOn(servicesApi, "getAllServices").mockImplementation(() => apiClient.request("/api/services"));

    const refreshButtons = screen.getAllByRole("button", { name: /Refresh/i });
    await act(async () => {
      fireEvent.click(refreshButtons[0]);
    });

    await waitFor(() => {
      expect(screen.getByText("LOGIN_PAGE_SHOWN")).toBeInTheDocument();
    });
  });

  it("8. Rapid repeated clicks do not cause harmful duplicate operations", async () => {
    let resolvePromise;
    const slowPromise = new Promise((resolve) => {
      resolvePromise = resolve;
    });

    const servicesSpy = vi.spyOn(servicesApi, "getAllServices")
      .mockResolvedValueOnce([
        { id: 1, name: "Alpha Service", active: true, environment: "PRODUCTION", ownerEmail: "technova@spectra.local" }
      ])
      .mockImplementation(() => slowPromise);

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/services"]}>
          <ProtectedWrapper role="USER" email="technova@spectra.local">
            <ServicesPage />
          </ProtectedWrapper>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("Alpha Service")).toBeInTheDocument();
    });

    const refreshButtons = screen.getAllByRole("button", { name: /Refresh/i });

    await act(async () => {
      fireEvent.click(refreshButtons[0]);
      fireEvent.click(refreshButtons[0]);
      fireEvent.click(refreshButtons[0]);
      fireEvent.click(refreshButtons[0]);
    });

    expect(servicesSpy).toHaveBeenCalledTimes(2);

    await act(async () => {
      resolvePromise([
        { id: 1, name: "Alpha Service", active: true, environment: "PRODUCTION", ownerEmail: "technova@spectra.local" }
      ]);
    });

    await waitFor(() => {
      expect(screen.getByText("Alpha Service")).toBeInTheDocument();
    });
  });

  it("9. Search and workspace filters remain functional", async () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/services"]}>
          <ProtectedWrapper role="ADMIN" email="operator@spectra.local">
            <ServicesPage />
          </ProtectedWrapper>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("Alpha Service")).toBeInTheDocument();
      expect(screen.getByText("Beta Service")).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText(/search services or owners/i);
    fireEvent.change(searchInput, { target: { value: "Alpha" } });

    expect(screen.getByText("Alpha Service")).toBeInTheDocument();
    expect(screen.queryByText("Beta Service")).not.toBeInTheDocument();

    const refreshButtons = screen.getAllByRole("button", { name: /Refresh/i });
    await act(async () => {
      fireEvent.click(refreshButtons[0]);
    });

    await waitFor(() => {
      expect(screen.getByText("Alpha Service")).toBeInTheDocument();
      expect(screen.queryByText("Beta Service")).not.toBeInTheDocument();
      expect(searchInput.value).toBe("Alpha");
    });
  });

  it("10. In-memory session policy requires re-authentication on full browser reload while in-page refresh maintains session", async () => {
    localStorage.setItem("spectra_auth_credentials", "legacy-creds");
    localStorage.setItem("spectra_auth_user", JSON.stringify({
      email: "operator@spectra.local",
      role: "ADMIN"
    }));

    function FullAppMock() {
      const { isAuthenticated } = useAuth();
      if (!isAuthenticated) {
        return <div>LOGIN_PAGE_REDIRECT</div>;
      }
      return (
        <div>
          <div>AUTHENTICATED_DASHBOARD</div>
          <DashboardPage />
        </div>
      );
    }

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/dashboard"]}>
          <FullAppMock />
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("LOGIN_PAGE_REDIRECT")).toBeInTheDocument();
      expect(screen.queryByText("AUTHENTICATED_DASHBOARD")).not.toBeInTheDocument();
      expect(localStorage.getItem("spectra_auth_credentials")).toBeNull();
      expect(localStorage.getItem("spectra_auth_user")).toBeNull();
    });
  });
});
