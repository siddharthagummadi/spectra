import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { IncidentsPage } from "../IncidentsPage";
import { AuthProvider } from "../../context/AuthContext";
import * as incidentsApi from "../../api/incidentsApi";
import * as servicesApi from "../../api/servicesApi";
import * as alertsApi from "../../api/alertsApi";
import * as authContext from "../../context/AuthContext";

describe("IncidentsPage", () => {
  const mockIncidents = [
    {
      id: 1,
      serviceName: "Gateway",
      title: "Outage in Gateway",
      severity: "CRITICAL",
      status: "OPEN",
      assignedTo: "SRE Team",
      triggeredAt: "2026-10-21T07:10:00Z"
    }
  ];

  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(servicesApi, "getAllServices").mockResolvedValue([{ id: 1, name: "Gateway", environment: "PRODUCTION" }]);
    vi.spyOn(alertsApi, "getAllAlerts").mockResolvedValue([]);
    vi.spyOn(incidentsApi, "getAllIncidents").mockResolvedValue(mockIncidents);
  });

  it("renders incidents table with severity and status", async () => {
    render(
      <AuthProvider>
        <MemoryRouter>
          <IncidentsPage />
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("Outage in Gateway")).toBeInTheDocument();
      expect(screen.getAllByText("CRITICAL").length).toBeGreaterThan(0);
      expect(screen.getAllByText("OPEN").length).toBeGreaterThan(0);
      expect(screen.getByText("SRE Team")).toBeInTheDocument();
    });
  });

  it("displays 409 conflict error when duplicate active incident is rejected", async () => {
    const error = new Error("An active incident already exists for alert ID 5");
    error.status = 409;
    vi.spyOn(incidentsApi, "createIncident").mockRejectedValue(error);

    render(
      <AuthProvider>
        <MemoryRouter>
          <IncidentsPage />
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("+ Create Incident")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("+ Create Incident"));

    const titleInput = screen.getByPlaceholderText(/e.g. Critical Latency/i);
    fireEvent.change(titleInput, { target: { value: "Duplicate Latency" } });

    fireEvent.click(screen.getByRole("button", { name: /^Create Incident$/i }));

    await waitFor(() => {
      expect(screen.getByText("An active incident already exists for alert ID 5")).toBeInTheDocument();
    });
  });

  it("allows user with USER role to create manual incident for their service", async () => {
    vi.spyOn(authContext, "useAuth").mockReturnValue({
      user: { email: "technova@spectra.local", role: "USER" },
      role: "USER",
      isAdmin: false,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn()
    });

    const createSpy = vi.spyOn(incidentsApi, "createIncident").mockResolvedValue({
      id: 2,
      serviceName: "Gateway",
      title: "Elevated Error Rates",
      severity: "HIGH",
      status: "OPEN",
      assignedTo: "technova@spectra.local",
      triggeredAt: "2026-10-21T08:00:00Z"
    });

    render(
      <MemoryRouter>
        <IncidentsPage />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("+ Create Incident")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("+ Create Incident"));
    expect(screen.getByText("Incident Title *")).toBeInTheDocument();

    const titleInput = screen.getByPlaceholderText(/e.g. Critical Latency/i);
    fireEvent.change(titleInput, { target: { value: "Elevated Error Rates" } });

    fireEvent.click(screen.getByRole("button", { name: /^Create Incident$/i }));

    await waitFor(() => {
      expect(createSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          title: "Elevated Error Rates",
          serviceId: 1,
          severity: "HIGH"
        })
      );
    });
  });
});
