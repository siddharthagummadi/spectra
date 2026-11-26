import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { AuthProvider, useAuth } from "../../context/AuthContext";
import { Sidebar } from "../../components/layout/Sidebar";
import { AdminOverviewPage } from "../AdminOverviewPage";
import { AdminEnterpriseDetailPage } from "../AdminEnterpriseDetailPage";
import { EnterpriseAgentsPage } from "../EnterpriseAgentsPage";
import * as adminApi from "../../api/adminApi";
import * as enterpriseApi from "../../api/enterpriseApi";

function AuthSeeder({ role, email, enterpriseName, children }) {
  const { login, isAuthenticated } = useAuth();
  const [ready, setReady] = React.useState(false);

  React.useEffect(() => {
    login({ email, role, enterpriseName }, "dGVzdC1jcmVkcw==");
    setReady(true);
  }, [email, role, enterpriseName, login]);

  if (!ready || !isAuthenticated) return null;
  return children;
}

describe("Multi-Enterprise Admin and Enterprise User Dashboards", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("renders Platform Admin navigation in Sidebar for ADMIN role", async () => {
    render(
      <AuthProvider>
        <MemoryRouter>
          <AuthSeeder role="ADMIN" email="admin@spectra.local">
            <Sidebar />
          </AuthSeeder>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("Platform Overview")).toBeInTheDocument();
      expect(screen.getByText("Enterprise Connections")).toBeInTheDocument();
      expect(screen.getByText("Agent Monitoring")).toBeInTheDocument();
      expect(screen.getByText("Audit Logs")).toBeInTheDocument();
      expect(screen.queryByText("Data Ingestion")).not.toBeInTheDocument();
    });
  });

  it("renders Enterprise User navigation with Monitoring Agents for USER role", async () => {
    render(
      <AuthProvider>
        <MemoryRouter>
          <AuthSeeder role="USER" email="sre@acme.test" enterpriseName="Acme Corp">
            <Sidebar />
          </AuthSeeder>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("Acme Corp")).toBeInTheDocument();
      expect(screen.getByText("Monitoring Agents")).toBeInTheDocument();
      expect(screen.queryByText("Enterprise Connections")).not.toBeInTheDocument();
    });
  });

  it("renders AdminOverviewPage with KPIs and connected enterprises", async () => {
    vi.spyOn(adminApi, "getPlatformOverview").mockResolvedValue({
      totalEnterprises: 2,
      connectedEnterprises: 1,
      pendingEnterprises: 1,
      degradedOrDelayedEnterprises: 0,
      disconnectedEnterprises: 0,
      totalMonitoredServices: 5,
      activeAlerts: 1,
      openIncidents: 1,
      totalAgents: 2,
      onlineAgents: 1,
      delayedAgents: 1,
      disconnectedAgents: 0,
      enterprises: [
        {
          id: 1,
          name: "Acme Financial",
          organizationCode: "ACME_PROD",
          environment: "PRODUCTION",
          connectionStatus: "CONNECTED",
          serviceCount: 5,
          activeServiceCount: 5,
          agentCount: 2,
          onlineAgentCount: 1,
          activeAlertCount: 1,
          openIncidentCount: 1,
          lastTelemetryAt: "2026-11-25T12:00:00Z"
        }
      ]
    });

    render(
      <AuthProvider>
        <MemoryRouter>
          <AdminOverviewPage />
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("Platform Admin Overview")).toBeInTheDocument();
      expect(screen.getByText("Acme Financial")).toBeInTheDocument();
      expect(screen.getByText("CONNECTED")).toBeInTheDocument();
    });
  });

  it("renders AdminEnterpriseDetailPage in strict read-only mode with zero write controls", async () => {
    vi.spyOn(adminApi, "getEnterpriseDashboard").mockResolvedValue({
      enterprise: {
        id: 1,
        name: "Acme Financial",
        organizationCode: "ACME_PROD",
        environment: "PRODUCTION",
        connectionStatus: "SYNC_DELAYED",
        statusReason: "Agent buffering active",
        activeServiceCount: 1,
        openIncidentCount: 1,
        onlineAgentCount: 1,
        lastTelemetryAt: "2026-11-25T12:00:00Z"
      },
      services: [{ id: 10, name: "Ledger-API", environment: "PRODUCTION", endpointUrl: "http://ledger.internal", active: true }],
      recentMetrics: [],
      alerts: [],
      anomalies: [],
      incidents: [{ id: 99, title: "High Latency", serviceName: "Ledger-API", severity: "HIGH", status: "OPEN", triggeredAt: "2026-11-25T12:00:00Z" }],
      agents: [{ id: 7, agentCode: "acme-01", hostname: "node-1", agentVersion: "1.0.0", status: "SYNC_DELAYED", localBufferCount: 4 }],
      readOnly: true
    });

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/admin/enterprises/1"]}>
          <Routes>
            <Route path="/admin/enterprises/:enterpriseId" element={<AdminEnterpriseDetailPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText("Read-Only Admin Inspection")).toBeInTheDocument();
      expect(screen.getByText(/Telemetry Integrity Warning/i)).toBeInTheDocument();
      expect(screen.queryByText(/Register Service/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/Create Incident/i)).not.toBeInTheDocument();
    });
  });

  it("renders stale telemetry alert banner on EnterpriseAgentsPage when agent is DISCONNECTED", async () => {
    vi.spyOn(enterpriseApi, "getMyEnterpriseAgents").mockResolvedValue([
      {
        id: 3,
        agentCode: "edge-agent-01",
        hostname: "edge-01",
        osInfo: "Linux 6.8",
        agentVersion: "1.0.0",
        status: "DISCONNECTED",
        collectionIntervalSec: 30,
        localBufferCount: 12,
        transmissionFailures: 3,
        lastHeartbeatAt: null,
        lastSuccessfulTelemetryAt: null
      }
    ]);

    render(
      <AuthProvider>
        <MemoryRouter>
          <AuthSeeder role="USER" email="sre@acme.test" enterpriseName="Acme Corp">
            <EnterpriseAgentsPage />
          </AuthSeeder>
        </MemoryRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText(/Stale or missing telemetry is not treated as healthy/i)).toBeInTheDocument();
      expect(screen.getByText("edge-agent-01")).toBeInTheDocument();
    });
  });
});
