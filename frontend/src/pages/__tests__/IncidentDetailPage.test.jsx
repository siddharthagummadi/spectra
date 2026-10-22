import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { IncidentDetailPage } from "../IncidentDetailPage";
import { AuthProvider } from "../../context/AuthContext";
import * as incidentsApi from "../../api/incidentsApi";

describe("IncidentDetailPage", () => {
  const mockIncident = {
    id: 1,
    title: "Database Outage",
    description: "Connection pool exhausted",
    serviceName: "Payment Gateway",
    severity: "CRITICAL",
    status: "OPEN",
    assignedTo: "SRE Lead",
    triggeredAt: "2026-10-21T07:10:00Z",
    resolvedAt: null,
    resolutionSummary: null
  };

  const mockNotes = [
    {
      id: 1,
      author: "Operator",
      noteText: "Incident created with status OPEN",
      createdAt: "2026-10-21T07:10:05Z"
    }
  ];

  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(incidentsApi, "getIncidentById").mockResolvedValue(mockIncident);
    vi.spyOn(incidentsApi, "getNotes").mockResolvedValue(mockNotes);
  });

  const renderWithRouter = () =>
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/incidents/1"]}>
          <Routes>
            <Route path="/incidents/:id" element={<IncidentDetailPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

  it("renders incident attributes and investigation notes", async () => {
    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText("Database Outage")).toBeInTheDocument();
      expect(screen.getByText("Connection pool exhausted")).toBeInTheDocument();
      expect(screen.getByText("Incident created with status OPEN")).toBeInTheDocument();
      expect(screen.getByText("Start Investigation →")).toBeInTheDocument();
    });
  });

  it("triggers transition to INVESTIGATING when requested", async () => {
    const transitionSpy = vi.spyOn(incidentsApi, "transitionStatus").mockResolvedValue({});
    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText("Start Investigation →")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("Start Investigation →"));
    expect(transitionSpy).toHaveBeenCalledWith(1, {
      status: "INVESTIGATING",
      author: "Operator",
      resolutionSummary: null
    });
  });

  it("allows submitting a new investigation note", async () => {
    const addNoteSpy = vi.spyOn(incidentsApi, "addNote").mockResolvedValue({});
    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText("Add Note")).toBeInTheDocument();
    });

    const noteInput = screen.getByPlaceholderText(/record investigative findings/i);
    fireEvent.change(noteInput, { target: { value: "Restarted connection pool" } });

    fireEvent.click(screen.getByText("Add Note"));
    expect(addNoteSpy).toHaveBeenCalledWith(1, {
      author: "Operator",
      noteText: "Restarted connection pool"
    });
  });
});
