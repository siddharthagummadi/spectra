import React, { useState, useEffect, useCallback } from "react";
import { getMyEnterpriseAgents } from "../api/enterpriseApi";
import { statusBadgeVariant } from "./AdminOverviewPage";
import { useAuth } from "../context/AuthContext";
import { Card } from "../components/common/Card";
import { Badge } from "../components/common/Badge";
import { Button } from "../components/common/Button";
import { AlertBanner } from "../components/common/AlertBanner";
import { LoadingSpinner } from "../components/common/LoadingSpinner";
import { EmptyState } from "../components/common/EmptyState";

export function EnterpriseAgentsPage() {
  const { user } = useAuth();
  const [agents, setAgents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadAgents = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await getMyEnterpriseAgents();
      setAgents(data || []);
    } catch (err) {
      setError(err.message || "Failed to load monitoring agents");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAgents();
  }, [loadAgents]);

  const staleAgents = agents.filter((a) => a.status === "DISCONNECTED" || a.status === "SYNC_DELAYED");

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 700, margin: 0 }}>Enterprise Monitoring Agents</h1>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 4 }}>
            Agent connectivity, heartbeat freshness, and telemetry sync status for{" "}
            <strong>{user?.enterpriseName || "your enterprise"}</strong>.
          </p>
        </div>
        <Button variant="secondary" onClick={loadAgents} loading={loading}>↻ Refresh</Button>
      </div>

      {error && <AlertBanner message={error} onClose={() => setError("")} />}

      {staleAgents.length > 0 && (
        <AlertBanner
          variant="warning"
          message={`${staleAgents.length} monitoring agent(s) are ${staleAgents.map((a) => `${a.agentCode} (${a.status})`).join(", ")}. Stale or missing telemetry is not treated as healthy.`}
        />
      )}

      <Card title={`Enrolled Agents (${agents.length})`}>
        {loading && agents.length === 0 ? (
          <LoadingSpinner label="Loading enterprise agents..." />
        ) : agents.length === 0 ? (
          <EmptyState
            title="No Monitoring Agent Enrolled Yet"
            description="Ask your Platform Administrator for an enrollment token and launch spectra-agent.jar in your infrastructure."
          />
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Agent Code</th>
                  <th>Hostname &amp; OS</th>
                  <th>Version</th>
                  <th>Status</th>
                  <th>Collection / Buffer</th>
                  <th>Last Heartbeat</th>
                  <th>Last Telemetry</th>
                </tr>
              </thead>
              <tbody>
                {agents.map((ag) => (
                  <tr key={ag.id}>
                    <td><code>{ag.agentCode}</code></td>
                    <td>
                      <div style={{ fontWeight: 500 }}>{ag.hostname}</div>
                      <div style={{ fontSize: 11, color: "var(--text-muted)" }}>{ag.osInfo || "-"}</div>
                    </td>
                    <td>{ag.agentVersion}</td>
                    <td><Badge variant={statusBadgeVariant(ag.status)}>{ag.status}</Badge></td>
                    <td style={{ fontSize: 12 }}>
                      Every {ag.collectionIntervalSec}s · Buffer: {ag.localBufferCount} · Failures: {ag.transmissionFailures}
                    </td>
                    <td style={{ fontSize: 12 }}>{ag.lastHeartbeatAt ? new Date(ag.lastHeartbeatAt).toLocaleString() : "Never"}</td>
                    <td style={{ fontSize: 12 }}>{ag.lastSuccessfulTelemetryAt ? new Date(ag.lastSuccessfulTelemetryAt).toLocaleString() : "Never"}</td>
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
