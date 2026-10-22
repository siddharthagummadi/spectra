import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { DashboardPage } from "../DashboardPage";
import * as servicesApi from "../../api/servicesApi";
import * as alertsApi from "../../api/alertsApi";
import * as incidentsApi from "../../api/incidentsApi";
import * as metricsApi from "../../api/metricsApi";
import * as authContext from "../../context/AuthContext";

describe("DashboardPage", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("calculates and displays operational overview cards from real API data", async () => {
    vi.spyOn(servicesApi, "getAllServices").mockResolvedValue([
      { id: 1, name: "Auth Svc", active: true, ownerEmail: "operator@spectra.local" },
      { id: 2, name: "Payment Svc", active: false, ownerEmail: "technova@spectra.local" }
    ]);

    vi.spyOn(alertsApi, "getAllRules").mockResolvedValue([
      { id: 1, ruleName: "CPU Rule", active: true }
    ]);

    vi.spyOn(alertsApi, "getAllAlerts").mockResolvedValue([
      { id: 1, ruleName: "CPU Rule", severity: "HIGH", status: "TRIGGERED", triggeredAt: "2026-10-21T07:00:00Z" }
    ]);

    vi.spyOn(incidentsApi, "getAllIncidents").mockResolvedValue([
      { id: 1, title: "DB Outage", status: "OPEN" },
      { id: 2, title: "Latency", status: "INVESTIGATING" }
    ]);

    vi.spyOn(metricsApi, "getRecentMetrics").mockResolvedValue([
      { id: 1, serviceName: "Auth Svc", metricName: "CPU_USAGE", metricValue: 75.5, unit: "PERCENT", recordedAt: "2026-10-21T07:00:00Z" }
    ]);

    render(
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("Operations Dashboard")).toBeInTheDocument();
      expect(screen.getByText(/active ·.*inactive/i)).toBeInTheDocument();
      expect(screen.getByText(/active rules configured/i)).toBeInTheDocument();
      expect(screen.getByText(/triggered ·.*acknowledged/i)).toBeInTheDocument();
      expect(screen.getByText(/open ·.*investigating/i)).toBeInTheDocument();
      expect(screen.getAllByText("Auth Svc").length).toBeGreaterThan(0);
      expect(screen.getByText("CPU Rule")).toBeInTheDocument();
    });
  });

  it("renders centralized enterprise overview table for ADMIN user with owner filtering", async () => {
    vi.spyOn(authContext, "useAuth").mockReturnValue({
      user: { email: "operator@spectra.local", role: "ADMIN" },
      role: "ADMIN",
      isAdmin: true,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn()
    });

    vi.spyOn(servicesApi, "getAllServices").mockResolvedValue([
      { id: 1, name: "Admin Core Service", environment: "PRODUCTION", active: true, endpointUrl: "https://core.spectra.local", ownerEmail: "operator@spectra.local" },
      { id: 2, name: "TechNova Payment Service", environment: "STAGING", active: true, endpointUrl: "https://pay.technova.local", ownerEmail: "technova@spectra.local" }
    ]);
    vi.spyOn(alertsApi, "getAllRules").mockResolvedValue([]);
    vi.spyOn(alertsApi, "getAllAlerts").mockResolvedValue([]);
    vi.spyOn(incidentsApi, "getAllIncidents").mockResolvedValue([]);
    vi.spyOn(metricsApi, "getRecentMetrics").mockResolvedValue([]);

    render(
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("Centralized Admin View")).toBeInTheDocument();
      expect(screen.getByText("Enterprise Monitored Services")).toBeInTheDocument();
      expect(screen.getByText("Admin Core Service")).toBeInTheDocument();
      expect(screen.getByText("TechNova Payment Service")).toBeInTheDocument();
      expect(screen.getAllByText("operator@spectra.local").length).toBeGreaterThan(0);
      expect(screen.getAllByText("technova@spectra.local").length).toBeGreaterThan(0);
      expect(screen.getByText("All Workspaces / Owners")).toBeInTheDocument();
    });
  });

  it("handles partial API failure on a single endpoint without crashing the page", async () => {
    vi.spyOn(servicesApi, "getAllServices").mockRejectedValue(new Error("Network timeout on services"));
    vi.spyOn(alertsApi, "getAllRules").mockResolvedValue([]);
    vi.spyOn(alertsApi, "getAllAlerts").mockResolvedValue([]);
    vi.spyOn(incidentsApi, "getAllIncidents").mockResolvedValue([]);
    vi.spyOn(metricsApi, "getRecentMetrics").mockResolvedValue([]);

    render(
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("Network timeout on services")).toBeInTheDocument();
      expect(screen.getByText("No active alerts")).toBeInTheDocument();
    });
  });
});
