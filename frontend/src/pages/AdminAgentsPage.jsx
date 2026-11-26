import React, { useState, useEffect, useCallback } from "react";
import { getAllAgents, rotateAgentCredential, deactivateAgent } from "../api/adminApi";
import { statusBadgeVariant } from "./AdminOverviewPage";
import { Card } from "../components/common/Card";
import { Badge } from "../components/common/Badge";
import { Button } from "../components/common/Button";
import { AlertBanner } from "../components/common/AlertBanner";
import { LoadingSpinner } from "../components/common/LoadingSpinner";
import { EmptyState } from "../components/common/EmptyState";

export function AdminAgentsPage() {
  const [agents, setAgents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [rotatedKey, setRotatedKey] = useState(null);

  const loadAgents = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await getAllAgents();
      setAgents(data || []);
    } catch (err) {
      setError(err.message || "Failed to load agents");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAgents();
  }, [loadAgents]);

  const handleRotate = async (agentId) => {
    setError("");
    try {
      const res = await rotateAgentCredential(agentId);
      setRotatedKey(res);
      await loadAgents();
    } catch (err) {
      setError(err.message || "Failed to rotate agent credential");
    }
  };

  const handleDeactivate = async (agentId) => {
    setError("");
    try {
      await deactivateAgent(agentId);
      await loadAgents();
    } catch (err) {
      setError(err.message || "Failed to deactivate agent");
    }
  };

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 700, margin: 0 }}>Monitoring Agents Oversight</h1>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 4 }}>
            Monitor agent connectivity, heartbeats, local buffer depth, failure counters, and credential rotation.
          </p>
        </div>
        <Button variant="secondary" onClick={loadAgents} loading={loading}>↻ Refresh</Button>
      </div>

      {error && <AlertBanner message={error} onClose={() => setError("")} />}

      {rotatedKey && (
        <Card
          title={`Rotated Credential for ${rotatedKey.agentCode}`}
          subtitle={`Previous credential grace period ends at ${new Date(rotatedKey.previousCredentialGraceEndsAt).toLocaleString()}`}
          actions={<Button variant="secondary" onClick={() => setRotatedKey(null)}>Dismiss</Button>}
          style={{ marginBottom: 20 }}
        >
          <pre style={{ backgroundColor: "var(--bg-subtle)", padding: 10, borderRadius: "var(--radius-sm)", overflowX: "auto" }}>
            {rotatedKey.agentCredential}
          </pre>
        </Card>
      )}

      <Card title={`Registered Agents (${agents.length})`}>
        {loading && agents.length === 0 ? (
          <LoadingSpinner label="Loading monitoring agents..." />
        ) : agents.length === 0 ? (
          <EmptyState
            title="No Monitoring Agents Enrolled"
            description="Agents appear here once enrolled using an enterprise's temporary enrollment token."
          />
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Agent</th>
                  <th>Enterprise</th>
                  <th>Version</th>
                  <th>Status</th>
                  <th>Buffer / Failures</th>
                  <th>Last Heartbeat</th>
                  <th>Last Telemetry</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {agents.map((ag) => (
                  <tr key={ag.id}>
                    <td>
                      <code>{ag.agentCode}</code>
                      <div style={{ fontSize: 11, color: "var(--text-muted)" }}>{ag.hostname}</div>
                    </td>
                    <td>{ag.enterpriseName}</td>
                    <td>{ag.agentVersion}</td>
                    <td><Badge variant={statusBadgeVariant(ag.status)}>{ag.status}</Badge></td>
                    <td style={{ fontSize: 12 }}>
                      Buf: {ag.localBufferCount} · ColErr: {ag.collectionFailures} · TxErr: {ag.transmissionFailures}
                    </td>
                    <td style={{ fontSize: 12 }}>{ag.lastHeartbeatAt ? new Date(ag.lastHeartbeatAt).toLocaleString() : "Never"}</td>
                    <td style={{ fontSize: 12 }}>{ag.lastSuccessfulTelemetryAt ? new Date(ag.lastSuccessfulTelemetryAt).toLocaleString() : "Never"}</td>
                    <td>
                      <div style={{ display: "flex", gap: 6 }}>
                        <Button variant="secondary" style={{ fontSize: 11, padding: "4px 8px" }} onClick={() => handleRotate(ag.id)} disabled={!ag.active}>
                          Rotate Key
                        </Button>
                        <Button variant="secondary" style={{ fontSize: 11, padding: "4px 8px", color: "var(--status-critical)" }} onClick={() => handleDeactivate(ag.id)} disabled={!ag.active}>
                          Deactivate
                        </Button>
                      </div>
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
