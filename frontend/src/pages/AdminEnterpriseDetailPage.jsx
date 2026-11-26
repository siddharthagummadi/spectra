import React, { useState, useEffect, useCallback } from "react";
import { useParams, Link } from "react-router-dom";
import { getEnterpriseDashboard } from "../api/adminApi";
import { statusBadgeVariant } from "./AdminOverviewPage";
import { Card } from "../components/common/Card";
import { Badge } from "../components/common/Badge";
import { Button } from "../components/common/Button";
import { LoadingSpinner } from "../components/common/LoadingSpinner";
import { AlertBanner } from "../components/common/AlertBanner";

export function AdminEnterpriseDetailPage() {
  const { enterpriseId } = useParams();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [activeTab, setActiveTab] = useState("services");

  const loadDetail = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const res = await getEnterpriseDashboard(enterpriseId);
      setData(res);
    } catch (err) {
      setError(err.message || "Failed to load enterprise monitoring dashboard");
    } finally {
      setLoading(false);
    }
  }, [enterpriseId]);

  useEffect(() => {
    loadDetail();
  }, [loadDetail]);

  if (loading && !data) {
    return <LoadingSpinner label="Loading Enterprise Read-Only Dashboard..." />;
  }

  const ent = data?.enterprise;
  const services = data?.services || [];
  const metrics = data?.recentMetrics || [];
  const alerts = data?.alerts || [];
  const anomalies = data?.anomalies || [];
  const incidents = data?.incidents || [];
  const agents = data?.agents || [];

  const activeAlerts = alerts.filter((a) => a.status !== "RESOLVED");

  return (
    <div>
      <div style={{ marginBottom: 12 }}>
        <Link to="/admin/overview" style={{ fontSize: 13, color: "var(--accent-primary)", textDecoration: "none" }}>
          ← Back to Platform Overview
        </Link>
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 18, flexWrap: "wrap", gap: 12 }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <h1 style={{ fontSize: 22, fontWeight: 700, margin: 0 }}>{ent?.name || `Enterprise #${enterpriseId}`}</h1>
            {ent?.connectionStatus && (
              <Badge variant={statusBadgeVariant(ent.connectionStatus)}>{ent.connectionStatus}</Badge>
            )}
            <Badge variant="info">Read-Only Admin Inspection</Badge>
          </div>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 4 }}>
            Org Code: <code>{ent?.organizationCode}</code> · Environment: {ent?.environment} · Last Telemetry:{" "}
            {ent?.lastTelemetryAt ? new Date(ent.lastTelemetryAt).toLocaleString() : "None yet"}
          </p>
        </div>
        <Button variant="secondary" onClick={loadDetail} loading={loading}>↻ Refresh</Button>
      </div>

      {error && <AlertBanner message={error} onClose={() => setError("")} />}

      {ent && (ent.connectionStatus === "DISCONNECTED" || ent.connectionStatus === "SYNC_DELAYED") && (
        <AlertBanner
          variant="warning"
          message={`Telemetry Integrity Warning: Enterprise status is ${ent.connectionStatus} (${ent.statusReason || "agent reporting delayed or offline"}). Missing telemetry does not indicate healthy services.`}
        />
      )}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 12, marginBottom: 20 }}>
        <Card title="Services">
          <div style={{ fontSize: 24, fontWeight: 700 }}>{services.length}</div>
          <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>{ent?.activeServiceCount ?? 0} active</div>
        </Card>
        <Card title="Active Alerts">
          <div style={{ fontSize: 24, fontWeight: 700, color: activeAlerts.length > 0 ? "var(--status-critical)" : "inherit" }}>
            {activeAlerts.length}
          </div>
          <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>{alerts.length} total historical</div>
        </Card>
        <Card title="Anomalies">
          <div style={{ fontSize: 24, fontWeight: 700 }}>{anomalies.length}</div>
          <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>Recent statistical &amp; ML events</div>
        </Card>
        <Card title="Incidents">
          <div style={{ fontSize: 24, fontWeight: 700 }}>{incidents.length}</div>
          <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>{ent?.openIncidentCount ?? 0} open</div>
        </Card>
        <Card title="Monitoring Agents">
          <div style={{ fontSize: 24, fontWeight: 700 }}>{agents.length}</div>
          <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>{ent?.onlineAgentCount ?? 0} online</div>
        </Card>
      </div>

      <div style={{ display: "flex", gap: 8, marginBottom: 16, flexWrap: "wrap" }}>
        {[
          { id: "services", label: `Services (${services.length})` },
          { id: "metrics", label: `Recent Metrics (${metrics.length})` },
          { id: "alerts", label: `Alerts (${alerts.length})` },
          { id: "anomalies", label: `Anomalies (${anomalies.length})` },
          { id: "incidents", label: `Incidents (${incidents.length})` },
          { id: "agents", label: `Agents (${agents.length})` }
        ].map((tab) => (
          <Button
            key={tab.id}
            variant={activeTab === tab.id ? "primary" : "secondary"}
            onClick={() => setActiveTab(tab.id)}
            style={{ fontSize: 12 }}
          >
            {tab.label}
          </Button>
        ))}
      </div>

      {activeTab === "services" && (
        <Card title="Monitored Services (Read-Only)">
          <table className="data-table">
            <thead>
              <tr><th>Name</th><th>Environment</th><th>Endpoint</th><th>Status</th></tr>
            </thead>
            <tbody>
              {services.map((s) => (
                <tr key={s.id}>
                  <td style={{ fontWeight: 600 }}>{s.name}</td>
                  <td>{s.environment}</td>
                  <td style={{ fontSize: 12 }}>{s.endpointUrl}</td>
                  <td><Badge variant={s.active ? "healthy" : "neutral"}>{s.active ? "ACTIVE" : "INACTIVE"}</Badge></td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {activeTab === "metrics" && (
        <Card title="Recent Service Metrics (Read-Only)">
          <table className="data-table">
            <thead>
              <tr><th>Service</th><th>Metric</th><th>Value</th><th>Recorded At</th></tr>
            </thead>
            <tbody>
              {metrics.map((m) => (
                <tr key={m.id}>
                  <td>{m.serviceName || `Service #${m.serviceId}`}</td>
                  <td>{m.metricName}</td>
                  <td style={{ fontWeight: 600 }}>{m.metricValue} {m.unit}</td>
                  <td style={{ fontSize: 12 }}>{new Date(m.recordedAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {activeTab === "alerts" && (
        <Card title="Alert History & Active Alerts (Read-Only)">
          <table className="data-table">
            <thead>
              <tr><th>Service</th><th>Rule</th><th>Severity</th><th>Status</th><th>Value</th><th>Triggered</th></tr>
            </thead>
            <tbody>
              {alerts.map((a) => (
                <tr key={a.id}>
                  <td>{a.serviceName}</td>
                  <td>{a.ruleName}</td>
                  <td><Badge variant={a.severity === "CRITICAL" ? "critical" : "warning"}>{a.severity}</Badge></td>
                  <td><Badge variant={a.status === "RESOLVED" ? "healthy" : "critical"}>{a.status}</Badge></td>
                  <td>{a.triggerValue} (threshold {a.thresholdValue} {a.unit})</td>
                  <td style={{ fontSize: 12 }}>{new Date(a.triggeredAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {activeTab === "anomalies" && (
        <Card title="Detected Anomalies (Read-Only)">
          <table className="data-table">
            <thead>
              <tr><th>Service</th><th>Metric</th><th>Observed</th><th>Score</th><th>Severity</th><th>Detected At</th></tr>
            </thead>
            <tbody>
              {anomalies.map((an) => (
                <tr key={an.id}>
                  <td>{an.serviceName}</td>
                  <td>{an.metricName}</td>
                  <td>{an.observedValue} {an.unit}</td>
                  <td>{an.anomalyScore}</td>
                  <td><Badge variant={an.severity === "CRITICAL" ? "critical" : "warning"}>{an.severity}</Badge></td>
                  <td style={{ fontSize: 12 }}>{new Date(an.detectedAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {activeTab === "incidents" && (
        <Card title="Incident History (Read-Only)">
          <table className="data-table">
            <thead>
              <tr><th>ID</th><th>Title</th><th>Service</th><th>Severity</th><th>Status</th><th>Triggered</th><th>Inspect</th></tr>
            </thead>
            <tbody>
              {incidents.map((inc) => (
                <tr key={inc.id}>
                  <td>#{inc.id}</td>
                  <td style={{ fontWeight: 600 }}>{inc.title}</td>
                  <td>{inc.serviceName}</td>
                  <td><Badge variant={inc.severity === "CRITICAL" ? "critical" : "warning"}>{inc.severity}</Badge></td>
                  <td><Badge variant={inc.status === "RESOLVED" || inc.status === "CLOSED" ? "healthy" : "warning"}>{inc.status}</Badge></td>
                  <td style={{ fontSize: 12 }}>{new Date(inc.triggeredAt).toLocaleString()}</td>
                  <td>
                    <Link to={`/incidents/${inc.id}`} style={{ fontSize: 12, color: "var(--accent-primary)" }}>
                      View Details
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      {activeTab === "agents" && (
        <Card title="Enterprise Monitoring Agents (Read-Only)">
          <table className="data-table">
            <thead>
              <tr><th>Agent Code</th><th>Hostname</th><th>Version</th><th>Status</th><th>Buffer</th><th>Last Heartbeat</th><th>Last Sync</th></tr>
            </thead>
            <tbody>
              {agents.map((ag) => (
                <tr key={ag.id}>
                  <td><code>{ag.agentCode}</code></td>
                  <td>{ag.hostname}</td>
                  <td>{ag.agentVersion}</td>
                  <td><Badge variant={statusBadgeVariant(ag.status)}>{ag.status}</Badge></td>
                  <td>{ag.localBufferCount} queued</td>
                  <td style={{ fontSize: 12 }}>{ag.lastHeartbeatAt ? new Date(ag.lastHeartbeatAt).toLocaleString() : "Never"}</td>
                  <td style={{ fontSize: 12 }}>{ag.lastSuccessfulTelemetryAt ? new Date(ag.lastSuccessfulTelemetryAt).toLocaleString() : "Never"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </div>
  );
}
