import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { AlertsPage } from "../AlertsPage";
import * as alertsApi from "../../api/alertsApi";
import * as servicesApi from "../../api/servicesApi";
import * as authContext from "../../context/AuthContext";

describe("AlertsPage", () => {
  const mockAlerts = [
    {
      id: 10,
      ruleName: "High CPU Alert",
      serviceName: "Payment Gateway",
      metricName: "CPU_USAGE",
      unit: "PERCENT",
      triggerValue: 92.0,
      thresholdValue: 80.0,
      severity: "CRITICAL",
      status: "TRIGGERED",
      message: "CPU is 92.0 PERCENT",
      triggeredAt: "2026-10-21T07:15:00Z"
    }
  ];

  const mockRules = [
    {
      id: 5,
      ruleName: "High CPU Alert",
      serviceName: "Payment Gateway",
      metricName: "CPU_USAGE",
      unit: "PERCENT",
      comparator: "GREATER_THAN",
      thresholdValue: 80.0,
      severity: "CRITICAL",
      active: true
    }
  ];

  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(servicesApi, "getAllServices").mockResolvedValue([{ id: 1, name: "Payment Gateway" }]);
    vi.spyOn(alertsApi, "getAllAlerts").mockResolvedValue(mockAlerts);
    vi.spyOn(alertsApi, "getAllRules").mockResolvedValue(mockRules);
  });

  it("renders alert events and supports acknowledgment", async () => {
    const ackSpy = vi.spyOn(alertsApi, "acknowledgeAlert").mockResolvedValue({});

    render(
      <MemoryRouter>
        <AlertsPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("High CPU Alert")).toBeInTheDocument();
      expect(screen.getByText("Payment Gateway")).toBeInTheDocument();
      expect(screen.getAllByText("TRIGGERED").length).toBeGreaterThan(0);
      expect(screen.getByText("Acknowledge")).toBeInTheDocument();
      expect(screen.getByText(/Alerts are generated automatically when submitted telemetry metrics breach/i)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("Acknowledge"));
    expect(ackSpy).toHaveBeenCalledWith(10);
  });

  it("switches to rules tab and toggles rule status as admin", async () => {
    const statusSpy = vi.spyOn(alertsApi, "updateRuleStatus").mockResolvedValue({});

    render(
      <MemoryRouter>
        <AlertsPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /Threshold Rules/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /Threshold Rules/i }));

    await waitFor(() => {
      expect(screen.getByText("ENABLED")).toBeInTheDocument();
      expect(screen.getByText("Disable")).toBeInTheDocument();
      expect(screen.getByText("+ Create Alert Rule")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("Disable"));
    expect(statusSpy).toHaveBeenCalledWith(5, false);
  });

  it("allows rule creation and management for USER role", async () => {
    vi.spyOn(authContext, "useAuth").mockReturnValue({
      user: { email: "technova@spectra.local", role: "USER" },
      role: "USER",
      isAdmin: false,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn()
    });

    const ackSpy = vi.spyOn(alertsApi, "acknowledgeAlert").mockResolvedValue({});
    const statusSpy = vi.spyOn(alertsApi, "updateRuleStatus").mockResolvedValue({});

    render(
      <MemoryRouter>
        <AlertsPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("Acknowledge")).toBeInTheDocument();
      expect(screen.getByText("+ Create Alert Rule")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("Acknowledge"));
    expect(ackSpy).toHaveBeenCalledWith(10);

    fireEvent.click(screen.getByRole("button", { name: /Threshold Rules/i }));

    await waitFor(() => {
      expect(screen.getByText("Disable")).toBeInTheDocument();
      expect(screen.getByText("Delete")).toBeInTheDocument();
      expect(screen.getByText("+ Create Alert Rule")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("Disable"));
    expect(statusSpy).toHaveBeenCalledWith(5, false);
  });

  it("shows appropriate empty states when services or rules are missing", async () => {
    vi.spyOn(servicesApi, "getAllServices").mockResolvedValue([]);
    vi.spyOn(alertsApi, "getAllRules").mockResolvedValue([]);

    render(
      <MemoryRouter>
        <AlertsPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /Threshold Rules/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /Threshold Rules/i }));

    await waitFor(() => {
      expect(screen.getByText("No monitored services found")).toBeInTheDocument();
      expect(screen.getByText("Register Service")).toBeInTheDocument();
    });
  });
});
