import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { MetricsPage } from "../MetricsPage";
import * as metricsApi from "../../api/metricsApi";
import * as servicesApi from "../../api/servicesApi";
import * as authContext from "../../context/AuthContext";

describe("MetricsPage", () => {
  const mockServices = [
    { id: 1, name: "Auth Service", environment: "PRODUCTION" }
  ];

  const mockMetrics = [
    {
      id: 1,
      serviceId: 1,
      serviceName: "Auth Service",
      metricName: "CPU_USAGE",
      metricValue: 82.5,
      unit: "PERCENT",
      recordedAt: "2026-10-21T07:00:00Z"
    },
    {
      id: 2,
      serviceId: 1,
      serviceName: "Auth Service",
      metricName: "RESPONSE_TIME",
      metricValue: 120.0,
      unit: "MILLISECONDS",
      recordedAt: "2026-10-21T07:05:00Z"
    }
  ];

  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(servicesApi, "getAllServices").mockResolvedValue(mockServices);
    vi.spyOn(metricsApi, "getRecentMetrics").mockResolvedValue(mockMetrics);
    vi.spyOn(metricsApi, "getMetricsByServiceId").mockResolvedValue(mockMetrics);
  });

  it("renders metrics list and SVG sparkline trend", async () => {
    render(
      <MemoryRouter>
        <MetricsPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("Telemetry Trend")).toBeInTheDocument();
      expect(screen.getByText("CPU_USAGE")).toBeInTheDocument();
      expect(screen.getByText("RESPONSE_TIME")).toBeInTheDocument();
      expect(screen.getByText("82.5")).toBeInTheDocument();
      expect(screen.getAllByText("120").length).toBeGreaterThan(0);
    });
  });

  it("allows user with USER role to open modal and submit new metric", async () => {
    vi.spyOn(authContext, "useAuth").mockReturnValue({
      user: { email: "technova@spectra.local", role: "USER" },
      role: "USER",
      isAdmin: false,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn()
    });

    const submitSpy = vi.spyOn(metricsApi, "submitMetric").mockResolvedValue({});

    render(
      <MemoryRouter>
        <MetricsPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("+ Submit Metric")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("+ Submit Metric"));
    expect(screen.getByText("Submit Service Metric")).toBeInTheDocument();

    const valInput = screen.getByPlaceholderText(/e.g. 75.5/i);
    fireEvent.change(valInput, { target: { value: "95" } });

    const submitBtn = screen.getByRole("button", { name: /^Submit Metric$/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(submitSpy).toHaveBeenCalledWith({
        serviceId: 1,
        metricName: "CPU_USAGE",
        metricValue: 95,
        unit: "PERCENT"
      });
    });
  });
});
