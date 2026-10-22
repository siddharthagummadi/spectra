import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { ServicesPage } from "../ServicesPage";
import * as servicesApi from "../../api/servicesApi";
import * as authContext from "../../context/AuthContext";

describe("ServicesPage", () => {
  const mockServices = [
    {
      id: 1,
      name: "Gateway API",
      description: "API Gateway proxy",
      endpointUrl: "https://gateway.example.com",
      environment: "PRODUCTION",
      active: true
    },
    {
      id: 2,
      name: "Worker Service",
      description: "Background worker",
      endpointUrl: "https://worker.example.com",
      environment: "DEVELOPMENT",
      active: false
    }
  ];

  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(servicesApi, "getAllServices").mockResolvedValue(mockServices);
  });

  it("renders services in a table with status badges", async () => {
    render(<ServicesPage />);

    await waitFor(() => {
      expect(screen.getByText("Gateway API")).toBeInTheDocument();
      expect(screen.getByText("Worker Service")).toBeInTheDocument();
      expect(screen.getAllByText("PRODUCTION").length).toBeGreaterThan(0);
      expect(screen.getAllByText("DEVELOPMENT").length).toBeGreaterThan(0);
      expect(screen.getByText("ACTIVE")).toBeInTheDocument();
      expect(screen.getByText("INACTIVE")).toBeInTheDocument();
    });
  });

  it("filters services based on search query", async () => {
    render(<ServicesPage />);

    await waitFor(() => {
      expect(screen.getByText("Gateway API")).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText(/search services/i);
    fireEvent.change(searchInput, { target: { value: "worker" } });

    expect(screen.queryByText("Gateway API")).not.toBeInTheDocument();
    expect(screen.getByText("Worker Service")).toBeInTheDocument();
  });

  it("invokes updateServiceStatus when toggling active state", async () => {
    const statusSpy = vi.spyOn(servicesApi, "updateServiceStatus").mockResolvedValue({});
    render(<ServicesPage />);

    await waitFor(() => {
      expect(screen.getByText("Deactivate")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("Deactivate"));
    expect(statusSpy).toHaveBeenCalledWith(1, false);
  });

  it("allows user with USER role to register and edit their services", async () => {
    vi.spyOn(authContext, "useAuth").mockReturnValue({
      user: { email: "technova@spectra.local", role: "USER" },
      role: "USER",
      isAdmin: false,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn()
    });

    const createSpy = vi.spyOn(servicesApi, "createService").mockResolvedValue({
      id: 3,
      name: "User Service",
      description: "User service desc",
      endpointUrl: "https://user.example.com",
      environment: "DEVELOPMENT",
      active: true
    });

    render(<ServicesPage />);

    await waitFor(() => {
      expect(screen.getByText("+ Register Service")).toBeInTheDocument();
      expect(screen.getAllByText("Edit").length).toBeGreaterThan(0);
      expect(screen.getByText("Deactivate")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("+ Register Service"));
    expect(screen.getByText("Register New Monitored Service")).toBeInTheDocument();

    const nameInput = screen.getByLabelText(/Service Name/i);
    fireEvent.change(nameInput, { target: { value: "User Service" } });

    const submitBtn = screen.getByRole("button", { name: /^Register Service$/i });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(createSpy).toHaveBeenCalledWith(
        expect.objectContaining({
          name: "User Service"
        })
      );
    });
  });

  it("shows Owner column for admin and displays Read-only for foreign services", async () => {
    vi.spyOn(authContext, "useAuth").mockReturnValue({
      user: { email: "operator@spectra.local", role: "ADMIN" },
      role: "ADMIN",
      isAdmin: true,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn()
    });

    vi.spyOn(servicesApi, "getAllServices").mockResolvedValue([
      {
        id: 10,
        name: "Operator Service",
        description: "Admin owned",
        endpointUrl: "https://op.spectra.local",
        environment: "PRODUCTION",
        active: true,
        ownerEmail: "operator@spectra.local"
      },
      {
        id: 20,
        name: "Foreign User Service",
        description: "User owned",
        endpointUrl: "https://user.spectra.local",
        environment: "STAGING",
        active: true,
        ownerEmail: "technova@spectra.local"
      }
    ]);

    render(<ServicesPage />);

    await waitFor(() => {
      expect(screen.getByText("Owner")).toBeInTheDocument();
      expect(screen.getAllByText("operator@spectra.local").length).toBeGreaterThan(0);
      expect(screen.getAllByText("technova@spectra.local").length).toBeGreaterThan(0);
      expect(screen.getByText("Edit")).toBeInTheDocument();
      expect(screen.getByText("Deactivate")).toBeInTheDocument();
      expect(screen.getByText("Read-only")).toBeInTheDocument();
    });
  });
});
