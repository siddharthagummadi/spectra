import React, { useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import { getPlatformOverview } from "../api/adminApi";
import { Card } from "../components/common/Card";
import { Badge } from "../components/common/Badge";
import { Button } from "../components/common/Button";
import { LoadingSpinner } from "../components/common/LoadingSpinner";
import { AlertBanner } from "../components/common/AlertBanner";
import { EmptyState } from "../components/common/EmptyState";

export function statusBadgeVariant(status) {
  switch (status) {
    case "CONNECTED":
    case "ONLINE":
      return "healthy";
    case "PENDING":
    case "SYNC_DELAYED":
      return "warning";
    case "DISCONNECTED":
    case "AUTHENTICATION_FAILED":
    case "INTEGRATION_ERROR":
      return "critical";
    default:
      return "neutral";
  }
}

export function AdminOverviewPage() {
  const [overview, setOverview] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadOverview = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await getPlatformOverview();
      setOverview(data);
    } catch (err) {
      setError(err.message || "Failed to load platform overview");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadOverview();
  }, [loadOverview]);

  useEffect(() => {
    window.addEventListener("spectra:refresh", loadOverview);
    return () => window.removeEventListener("spectra:refresh", loadOverview);
  }, [loadOverview]);

  if (loading && !overview) {
    return <LoadingSpinner label="Loading Platform Admin Overview..." />;
  }

  const enterprises = overview?.enterprises || [];

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20, flexWrap: "wrap", gap: 12 }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <h1 style={{ fontSize: 22, fontWeight: 700, margin: 0 }}>Platform Admin Overview</h1>
            <Badge variant="info">Read-Only Operational Oversight</Badge>
          </div>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 4 }}>
            Centralized health, connectivity, and monitoring agent status across all onboarded enterprises.
          </p>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <Link to="/admin/enterprises" style={{ textDecoration: "none" }}>
            <Button variant="primary">+ Connect Enterprise</Button>
          </Link>
          <Button variant="secondary" onClick={loadOverview} loading={loading}>
            ↻ Refresh
          </Button>
        </div>
      </div>

      {error && <AlertBanner message={error} onClose={() => setError("")} />}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(190px, 1fr))", gap: 14, marginBottom: 24 }}>
        <Card title="Connected Enterprises">
          <div style={{ fontSize: 28, fontWeight: 700 }}>{overview?.totalEnterprises ?? 0}</div>
          <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 4 }}>
            <span style={{ color: "var(--status-healthy)", fontWeight: 600 }}>{overview?.connectedEnterprises ?? 0}</span> connected ·{" "}
            {overview?.pendingEnterprises ?? 0} pending
          </div>
        </Card>

        <Card title="Enterprise Health Split">
          <div style={{ fontSize: 28, fontWeight: 700, color: (overview?.degradedOrDelayedEnterprises || 0) > 0 ? "var(--status-warning)" : "var(--status-healthy)" }}>
            {overview?.degradedOrDelayedEnterprises ?? 0} <span style={{ fontSize: 14, fontWeight: 500 }}>delayed/error</span>
          </div>
          <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 4 }}>
            <span style={{ color: "var(--status-critical)", fontWeight: 600 }}>{overview?.disconnectedEnterprises ?? 0}</span> disconnected
          </div>
        </Card>

        <Card title="Monitored Services">
          <div style={{ fontSize: 28, fontWeight: 700 }}>{overview?.totalMonitoredServices ?? 0}</div>
          <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 4 }}>
            Across all active tenants
          </div>
        </Card>

        <Card title="Active Alerts & Incidents">
          <div style={{ fontSize: 28, fontWeight: 700, color: (overview?.openIncidents || 0) > 0 ? "var(--status-critical)" : "var(--text-primary)" }}>
            {overview?.activeAlerts ?? 0} <span style={{ fontSize: 13, fontWeight: 500 }}>alerts</span>
          </div>
          <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 4 }}>
            <span style={{ fontWeight: 600 }}>{overview?.openIncidents ?? 0}</span> open incidents
          </div>
        </Card>

        <Card title="Monitoring Agents">
          <div style={{ fontSize: 28, fontWeight: 700 }}>{overview?.totalAgents ?? 0}</div>
          <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 4 }}>
            <span style={{ color: "var(--status-healthy)", fontWeight: 600 }}>{overview?.onlineAgents ?? 0}</span> online ·{" "}
            {overview?.delayedAgents ?? 0} delayed · {overview?.disconnectedAgents ?? 0} offline
          </div>
        </Card>
      </div>

      <Card
        title="Connected Enterprises"
        subtitle="Select any enterprise to inspect its read-only monitoring dashboard"
        actions={<Link to="/admin/agents" style={{ fontSize: 12, color: "var(--accent-primary)" }}>View All Agents →</Link>}
      >
        {enterprises.length === 0 ? (
          <EmptyState
            title="No enterprises onboarded yet"
            description="Click 'Connect Enterprise' to register an organization and issue an agent enrollment token."
          />
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Enterprise</th>
                  <th>Org Code</th>
                  <th>Status</th>
                  <th>Services</th>
                  <th>Agents</th>
                  <th>Active Alerts</th>
                  <th>Open Incidents</th>
                  <th>Last Telemetry</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {enterprises.map((ent) => (
                  <tr key={ent.id}>
                    <td>
                      <div style={{ fontWeight: 600 }}>{ent.name}</div>
                      <div style={{ fontSize: 11, color: "var(--text-muted)" }}>{ent.environment}</div>
                    </td>
                    <td><code>{ent.organizationCode}</code></td>
                    <td><Badge variant={statusBadgeVariant(ent.connectionStatus)}>{ent.connectionStatus}</Badge></td>
                    <td>{ent.activeServiceCount} / {ent.serviceCount}</td>
                    <td>{ent.onlineAgentCount} / {ent.agentCount} online</td>
                    <td>{ent.activeAlertCount}</td>
                    <td>{ent.openIncidentCount}</td>
                    <td style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                      {ent.lastTelemetryAt ? new Date(ent.lastTelemetryAt).toLocaleString() : "Awaiting telemetry"}
                    </td>
                    <td>
                      <Link to={`/admin/enterprises/${ent.id}`} style={{ textDecoration: "none" }}>
                        <Button variant="secondary" style={{ fontSize: 12, padding: "4px 10px" }}>
                          Inspect Dashboard
                        </Button>
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
