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
    // Default: 404 (no existing diagnostics)
    vi.spyOn(incidentsApi, "getIncidentAiDiagnostics").mockRejectedValue({
      status: 404,
      message: "No AI diagnostics found"
    });
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

  // --- STAGE 2.4 TEST SUITE ---

  // 1. No existing diagnostics (404 state)
  it("renders prompt to run diagnostics when no existing analysis exists", async () => {
    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText(/no ai diagnostics have been run for this incident yet/i)).toBeInTheDocument();
      expect(screen.getByText("Execute AI Diagnosis ⚡")).toBeInTheDocument();
    });
  });

  // 2. Successful diagnostics display (COMPLETED state)
  it("renders full diagnostics information when analysis status is COMPLETED", async () => {
    const mockCompleted = {
      incidentId: 1,
      latestExecution: {
        id: 101,
        incidentId: 1,
        status: "COMPLETED",
        primaryRootCause: "Database Connection Pool Starvation",
        failureCategory: "RESOURCE_EXHAUSTION",
        confidenceScore: 0.94,
        primaryEvidence: "HikariCP active connections reached max pool limit (100)",
        rankedCauses: [{ cause: "Connection Pool Starvation", confidence: 0.94 }],
        contributingFactors: ["High concurrent checkout requests", "Slow reporting queries"],
        recommendedRunbooks: [
          {
            runbook_id: "RB-DB-01",
            title: "Expand Pool & Evict Stale Connections",
            immediate_actions: ["Scale pool size to 150", "Kill idle sessions"],
            severity_tier: "P1"
          }
        ],
        version: 1,
        executionId: "exec-abcd-1234",
        completedAt: "2026-10-21T07:15:00Z"
      },
      lastSuccessful: {
        id: 101,
        incidentId: 1,
        status: "COMPLETED",
        primaryRootCause: "Database Connection Pool Starvation",
        failureCategory: "RESOURCE_EXHAUSTION",
        confidenceScore: 0.94,
        primaryEvidence: "HikariCP active connections reached max pool limit (100)",
        version: 1
      }
    };

    vi.spyOn(incidentsApi, "getIncidentAiDiagnostics").mockResolvedValue(mockCompleted);

    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText("Database Connection Pool Starvation")).toBeInTheDocument();
      expect(screen.getByText("94% Confidence")).toBeInTheDocument();
      expect(screen.getByText("RESOURCE_EXHAUSTION")).toBeInTheDocument();
      expect(screen.getByText(/hikaricp active connections reached max pool limit/i)).toBeInTheDocument();
      expect(screen.getByText("High concurrent checkout requests")).toBeInTheDocument();
      expect(screen.getByText(/expand pool & evict stale connections/i)).toBeInTheDocument();
      expect(screen.getByText("Scale pool size to 150")).toBeInTheDocument();
      expect(screen.getByText("COMPLETED (V1)")).toBeInTheDocument();
      expect(screen.getByText("Re-diagnose ⚡")).toBeInTheDocument();
    });
  });

  // 3. PROCESSING state
  it("renders in-progress status when analysis is PROCESSING", async () => {
    const mockProcessing = {
      incidentId: 1,
      latestExecution: {
        id: 102,
        incidentId: 1,
        status: "PROCESSING",
        version: 2,
        executionId: "exec-proc-999"
      },
      lastSuccessful: null
    };

    vi.spyOn(incidentsApi, "getIncidentAiDiagnostics").mockResolvedValue(mockProcessing);

    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText(/ai diagnostics in progress \(version #2\)/i)).toBeInTheDocument();
      expect(screen.getByText(/evaluating telemetry metrics, alert events/i)).toBeInTheDocument();
    });
  });

  // 4. FAILED state
  it("renders failure reason and retry button when analysis status is FAILED", async () => {
    const mockFailed = {
      incidentId: 1,
      latestExecution: {
        id: 103,
        incidentId: 1,
        status: "FAILED",
        version: 1,
        failureReason: "AI microservice invocation failed: Read timed out",
        executionId: "exec-fail-111"
      },
      lastSuccessful: null
    };

    vi.spyOn(incidentsApi, "getIncidentAiDiagnostics").mockResolvedValue(mockFailed);

    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText(/latest ai analysis failed \(version #1\)/i)).toBeInTheDocument();
      expect(screen.getByText(/ai microservice invocation failed: read timed out/i)).toBeInTheDocument();
      expect(screen.getByText("Retry Diagnostics ↻")).toBeInTheDocument();
    });
  });

  // 5. Failed reanalysis with previous successful diagnosis retained
  it("preserves and displays previous successful diagnosis when latest reanalysis is FAILED", async () => {
    const mockFailedReanalysis = {
      incidentId: 1,
      latestExecution: {
        id: 104,
        incidentId: 1,
        status: "FAILED",
        version: 2,
        failureReason: "Python microservice unavailable (503)",
        executionId: "exec-fail-222"
      },
      lastSuccessful: {
        id: 101,
        incidentId: 1,
        status: "COMPLETED",
        version: 1,
        primaryRootCause: "Initial Confirmed Heap Exhaustion",
        failureCategory: "MEMORY_LEAK",
        confidenceScore: 0.91,
        primaryEvidence: "JVM Heap reached 95% utilization before OOM",
        contributingFactors: ["Memory leak in session cache"],
        recommendedRunbooks: [
          {
            runbook_id: "RB-JVM-02",
            title: "Heap Dump & Rolling Restart",
            immediate_actions: ["Generate heap dump", "Execute rolling restart"],
            severity_tier: "P1"
          }
        ]
      }
    };

    vi.spyOn(incidentsApi, "getIncidentAiDiagnostics").mockResolvedValue(mockFailedReanalysis);

    renderWithRouter();

    await waitFor(() => {
      // Must show the latest failure
      expect(screen.getByText(/latest ai analysis failed \(version #2\)/i)).toBeInTheDocument();
      expect(screen.getByText(/python microservice unavailable \(503\)/i)).toBeInTheDocument();

      // Must also show the retained previous successful diagnosis
      expect(screen.getByText(/retained previous successful diagnosis \(version #1\)/i)).toBeInTheDocument();
      expect(screen.getByText("Initial Confirmed Heap Exhaustion")).toBeInTheDocument();
      expect(screen.getByText("91% Confidence")).toBeInTheDocument();
      expect(screen.getByText(/heap dump & rolling restart/i)).toBeInTheDocument();
    });
  });

  // 6. Trigger diagnostics action (initial run)
  it("triggers initial diagnostics with forceReanalyze=false and updates UI", async () => {
    const triggerSpy = vi.spyOn(incidentsApi, "triggerIncidentAiDiagnostics").mockResolvedValue({
      id: 105,
      status: "COMPLETED",
      primaryRootCause: "Thread Deadlock",
      version: 1
    });

    const getDiagSpy = vi.spyOn(incidentsApi, "getIncidentAiDiagnostics")
      .mockRejectedValueOnce({ status: 404 }) // Initial load
      .mockResolvedValueOnce({
        incidentId: 1,
        latestExecution: {
          id: 105,
          status: "COMPLETED",
          primaryRootCause: "Thread Deadlock",
          version: 1,
          confidenceScore: 0.88
        },
        lastSuccessful: {
          id: 105,
          status: "COMPLETED",
          primaryRootCause: "Thread Deadlock",
          version: 1,
          confidenceScore: 0.88
        }
      });

    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText("Execute AI Diagnosis ⚡")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("Execute AI Diagnosis ⚡"));

    await waitFor(() => {
      expect(triggerSpy).toHaveBeenCalledWith(1, false);
      expect(getDiagSpy).toHaveBeenCalledTimes(2);
      expect(screen.getByText("Thread Deadlock")).toBeInTheDocument();
    });
  });

  // 7. Forced reanalysis
  it("triggers forced reanalysis with forceReanalyze=true when Re-diagnose is clicked", async () => {
    const triggerSpy = vi.spyOn(incidentsApi, "triggerIncidentAiDiagnostics").mockResolvedValue({
      id: 106,
      status: "COMPLETED",
      version: 2
    });

    vi.spyOn(incidentsApi, "getIncidentAiDiagnostics").mockResolvedValue({
      incidentId: 1,
      latestExecution: {
        id: 101,
        status: "COMPLETED",
        primaryRootCause: "Old Cause",
        version: 1
      },
      lastSuccessful: {
        id: 101,
        status: "COMPLETED",
        primaryRootCause: "Old Cause",
        version: 1
      }
    });

    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText("Re-diagnose ⚡")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("Re-diagnose ⚡"));

    await waitFor(() => {
      expect(triggerSpy).toHaveBeenCalledWith(1, true);
    });
  });

  // 8. Duplicate-click prevention
  it("disables trigger button and prevents duplicate calls while execution is in progress", async () => {
    let resolveTrigger;
    const pendingPromise = new Promise((resolve) => {
      resolveTrigger = resolve;
    });

    const triggerSpy = vi.spyOn(incidentsApi, "triggerIncidentAiDiagnostics").mockReturnValue(pendingPromise);

    vi.spyOn(incidentsApi, "getIncidentAiDiagnostics").mockResolvedValue({
      incidentId: 1,
      latestExecution: {
        id: 101,
        status: "COMPLETED",
        primaryRootCause: "Existing Cause",
        version: 1
      },
      lastSuccessful: null
    });

    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText("Re-diagnose ⚡")).toBeInTheDocument();
    });

    const btn = screen.getByText("Re-diagnose ⚡");
    fireEvent.click(btn);

    // Button should enter loading/disabled state
    await waitFor(() => {
      expect(screen.getByText("Please wait...")).toBeDisabled();
    });

    // Secondary click should not trigger another call
    fireEvent.click(screen.getByText("Please wait..."));
    expect(triggerSpy).toHaveBeenCalledTimes(1);

    resolveTrigger({ id: 107, status: "PROCESSING" });
  });

  // 9. API errors and unauthorized responses
  it("handles 403 Forbidden and 401 Unauthorized errors gracefully", async () => {
    // Case A: 403 on trigger
    vi.spyOn(incidentsApi, "triggerIncidentAiDiagnostics").mockRejectedValue({
      status: 403,
      message: "Forbidden"
    });

    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText("Execute AI Diagnosis ⚡")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("Execute AI Diagnosis ⚡"));

    await waitFor(() => {
      expect(screen.getByText(/access denied: you do not have permission to run ai diagnostics/i)).toBeInTheDocument();
    });
  });

  // 10. Optional or missing response fields
  it("safely handles null or missing optional fields without crashing", async () => {
    const minimalCompleted = {
      incidentId: 1,
      latestExecution: {
        id: 108,
        incidentId: 1,
        status: "COMPLETED",
        primaryRootCause: "Degraded Network Link",
        failureCategory: null,
        confidenceScore: null,
        primaryEvidence: null,
        rankedCauses: null,
        contributingFactors: null,
        recommendedRunbooks: null,
        version: 1
      },
      lastSuccessful: null
    };

    vi.spyOn(incidentsApi, "getIncidentAiDiagnostics").mockResolvedValue(minimalCompleted);

    renderWithRouter();

    await waitFor(() => {
      expect(screen.getByText("Degraded Network Link")).toBeInTheDocument();
      expect(screen.queryByText(/% Confidence/)).not.toBeInTheDocument();
      expect(screen.getByText("COMPLETED (V1)")).toBeInTheDocument();
    });
  });
});
