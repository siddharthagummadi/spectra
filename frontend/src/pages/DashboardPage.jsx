import React, { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { getAllServices } from "../api/servicesApi";
import { getAllRules, getAllAlerts } from "../api/alertsApi";
import { getAllIncidents } from "../api/incidentsApi";
import { getRecentMetrics } from "../api/metricsApi";
import { Card } from "../components/common/Card";
import { Badge } from "../components/common/Badge";
import { Button } from "../components/common/Button";
import { LoadingSpinner } from "../components/common/LoadingSpinner";
import { EmptyState } from "../components/common/EmptyState";
import { AlertBanner } from "../components/common/AlertBanner";

export function DashboardPage() {
  const { user, isAdmin } = useAuth();
  const [loading, setLoading] = useState(true);
  const [globalError, setGlobalError] = useState("");
  const isRefreshingRef = useRef(false);

  const [data, setData] = useState({
    services: [],
    rules: [],
    alerts: [],
    incidents: [],
    metrics: []
  });
  const [errors, setErrors] = useState({});

  const [serviceSearch, setServiceSearch] = useState("");
  const [ownerFilter, setOwnerFilter] = useState("ALL");
  const [envFilter, setEnvFilter] = useState("ALL");
  const [statusFilter, setStatusFilter] = useState("ALL");

  const loadDashboard = useCallback(async () => {
    if (isRefreshingRef.current) return;
    isRefreshingRef.current = true;
    setLoading(true);
    setGlobalError("");

    try {
      const results = await Promise.allSettled([
        getAllServices(),
        getAllRules(),
        getAllAlerts(),
        getAllIncidents(),
        getRecentMetrics(8)
      ]);

      const nextErrors = {};
      let failureCount = 0;

      setData((prev) => {
        const nextData = { ...prev };

        if (results[0].status === "fulfilled") {
          nextData.services = results[0].value || [];
        } else {
          failureCount++;
          nextErrors.services = results[0].reason?.message || "Failed to load services";
        }

        if (results[1].status === "fulfilled") {
          nextData.rules = results[1].value || [];
        } else {
          failureCount++;
          nextErrors.rules = results[1].reason?.message || "Failed to load rules";
        }

        if (results[2].status === "fulfilled") {
          nextData.alerts = results[2].value || [];
        } else {
          failureCount++;
          nextErrors.alerts = results[2].reason?.message || "Failed to load alerts";
        }

        if (results[3].status === "fulfilled") {
          nextData.incidents = results[3].value || [];
        } else {
          failureCount++;
          nextErrors.incidents = results[3].reason?.message || "Failed to load incidents";
        }

        if (results[4].status === "fulfilled") {
          nextData.metrics = results[4].value || [];
        } else {
          failureCount++;
          nextErrors.metrics = results[4].reason?.message || "Failed to load metrics";
        }

        return nextData;
      });

      if (failureCount === 5) {
        setGlobalError("Unable to refresh dashboard telemetry. Please check server connectivity.");
      }

      setErrors(nextErrors);
    } finally {
      setLoading(false);
      isRefreshingRef.current = false;
    }
  }, []);

  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);

  useEffect(() => {
    const handleGlobalRefresh = () => {
      loadDashboard();
    };
    window.addEventListener("spectra:refresh", handleGlobalRefresh);
    return () => window.removeEventListener("spectra:refresh", handleGlobalRefresh);
  }, [loadDashboard]);

  const activeServices = data.services.filter((s) => s.active);
  const activeRules = data.rules.filter((r) => r.active);
  const triggeredAlerts = data.alerts.filter((a) => a.status === "TRIGGERED");
  const acknowledgedAlerts = data.alerts.filter((a) => a.status === "ACKNOWLEDGED");
  const openIncidents = data.incidents.filter((i) => i.status === "OPEN");
  const investigatingIncidents = data.incidents.filter((i) => i.status === "INVESTIGATING");
  const unresolvedAlerts = data.alerts.filter((a) => a.status !== "RESOLVED");

  const uniqueOwners = useMemo(() => {
    const set = new Set();
    data.services.forEach((s) => {
      if (s.ownerEmail) set.add(s.ownerEmail);
    });
    return Array.from(set).sort();
  }, [data.services]);

  const filteredEnterpriseServices = useMemo(() => {
    return data.services.filter((s) => {
      const q = serviceSearch.trim().toLowerCase();
      const matchesSearch = !q ||
        s.name.toLowerCase().includes(q) ||
        (s.description && s.description.toLowerCase().includes(q)) ||
        (s.ownerEmail && s.ownerEmail.toLowerCase().includes(q));
      const matchesOwner = ownerFilter === "ALL" || s.ownerEmail === ownerFilter;
      const matchesEnv = envFilter === "ALL" || s.environment === envFilter;
      const matchesStatus = statusFilter === "ALL" ||
        (statusFilter === "ACTIVE" && s.active) ||
        (statusFilter === "INACTIVE" && !s.active);
      return matchesSearch && matchesOwner && matchesEnv && matchesStatus;
    });
  }, [data.services, serviceSearch, ownerFilter, envFilter, statusFilter]);

  const hasAnyData = data.services.length > 0 || data.metrics.length > 0 || data.alerts.length > 0;

  return (
    <div style={{ maxWidth: 1280, margin: "0 auto", padding: "4px 0" }}>
      {/* Top Header & Role Actions */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20, flexWrap: "wrap", gap: 14 }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <h1 style={{ fontSize: 22, fontWeight: 700, margin: 0, color: "var(--text-primary)" }}>
              Operations Dashboard
            </h1>
            <Badge variant={isAdmin ? "active" : "neutral"}>
              {isAdmin ? "Centralized Admin View" : "Operator Workspace"}
            </Badge>
          </div>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 4, marginBottom: 0 }}>
            {isAdmin
              ? "Centralized enterprise operations, cross-workspace telemetry, and incident governance."
              : "Real-time telemetry, service health monitoring, and active incident response."}
          </p>
        </div>

        {/* Action Buttons */}
        <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
          <Link
            to="/ingestion"
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: 6,
              padding: "7px 14px",
              backgroundColor: "var(--accent-primary)",
              color: "#fff",
              borderRadius: "var(--radius-sm)",
              fontSize: 12,
              fontWeight: 600,
              textDecoration: "none"
            }}
          >
            <span>📥 Ingest Dataset</span>
          </Link>

          {isAdmin && (
            <>
              <Link
                to="/datasets"
                style={{
                  display: "inline-flex",
                  alignItems: "center",
                  gap: 4,
                  padding: "6px 12px",
                  backgroundColor: "var(--bg-surface)",
                  border: "1px solid var(--border-default)",
                  borderRadius: "var(--radius-sm)",
                  color: "var(--text-primary)",
                  fontSize: 12,
                  fontWeight: 500,
                  textDecoration: "none"
                }}
              >
                <span>📁 Batches</span>
              </Link>
              <Link
                to="/evaluation"
                style={{
                  display: "inline-flex",
                  alignItems: "center",
                  gap: 4,
                  padding: "6px 12px",
                  backgroundColor: "var(--bg-surface)",
                  border: "1px solid var(--border-default)",
                  borderRadius: "var(--radius-sm)",
                  color: "var(--text-primary)",
                  fontSize: 12,
                  fontWeight: 500,
                  textDecoration: "none"
                }}
              >
                <span>📊 Model Evaluation</span>
              </Link>
              <Link
                to="/integrations"
                style={{
                  display: "inline-flex",
                  alignItems: "center",
                  gap: 4,
                  padding: "6px 12px",
                  backgroundColor: "var(--bg-surface)",
                  border: "1px solid var(--border-default)",
                  borderRadius: "var(--radius-sm)",
                  color: "var(--text-primary)",
                  fontSize: 12,
                  fontWeight: 500,
                  textDecoration: "none"
                }}
              >
                <span>🏢 Integrations</span>
              </Link>
            </>
          )}

          <Button variant="secondary" onClick={loadDashboard} loading={loading} disabled={loading}>
            ↻ Refresh
          </Button>
        </div>
      </div>

      {globalError && (
        <AlertBanner message={globalError} onClose={() => setGlobalError("")} />
      )}

      {/* Primary KPI Cards */}
      <div style={{
        display: "grid",
        gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
        gap: 16,
        marginBottom: 24
      }}>
        <Card title={isAdmin ? "Enterprise Services" : "Registered Services"}>
          {errors.services ? (
            <div style={{ color: "var(--status-critical)", fontSize: 12 }}>{errors.services}</div>
          ) : (
            <div>
              <div style={{ fontSize: 28, fontWeight: 700, color: "var(--text-primary)" }}>
                {data.services.length}
              </div>
              <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 4 }}>
                <span style={{ color: "var(--status-healthy)", fontWeight: 600 }}>{activeServices.length}</span> active · {data.services.length - activeServices.length} inactive
              </div>
            </div>
          )}
        </Card>

        <Card title="Threshold Rules">
          {errors.rules ? (
            <div style={{ color: "var(--status-critical)", fontSize: 12 }}>{errors.rules}</div>
          ) : (
            <div>
              <div style={{ fontSize: 28, fontWeight: 700, color: "var(--text-primary)" }}>
                {data.rules.length}
              </div>
              <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 4 }}>
                <span style={{ color: "var(--accent-primary)", fontWeight: 600 }}>{activeRules.length}</span> active rules configured
              </div>
            </div>
          )}
        </Card>

        <Card title="Active Alerts">
          {errors.alerts ? (
            <div style={{ color: "var(--status-critical)", fontSize: 12 }}>{errors.alerts}</div>
          ) : (
            <div>
              <div style={{
                fontSize: 28,
                fontWeight: 700,
                color: triggeredAlerts.length > 0 ? "var(--status-critical)" : "var(--text-primary)"
              }}>
                {triggeredAlerts.length + acknowledgedAlerts.length}
              </div>
              <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 4 }}>
                <span style={{ color: "var(--status-critical)", fontWeight: 600 }}>{triggeredAlerts.length}</span> triggered · {acknowledgedAlerts.length} acknowledged
              </div>
            </div>
          )}
        </Card>

        <Card title="Open Incidents">
          {errors.incidents ? (
            <div style={{ color: "var(--status-critical)", fontSize: 12 }}>{errors.incidents}</div>
          ) : (
            <div>
              <div style={{
                fontSize: 28,
                fontWeight: 700,
                color: openIncidents.length > 0 ? "var(--status-critical)" : "var(--text-primary)"
              }}>
                {openIncidents.length + investigatingIncidents.length}
              </div>
              <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 4 }}>
                <span style={{ color: "var(--status-critical)", fontWeight: 600 }}>{openIncidents.length}</span> open · {investigatingIncidents.length} investigating
              </div>
            </div>
          )}
        </Card>
      </div>

      {!loading && !hasAnyData && (
        <div style={{
          backgroundColor: "var(--bg-surface)",
          border: "1px solid var(--border-default)",
          borderRadius: "var(--radius-md)",
          padding: 48,
          textAlign: "center",
          marginBottom: 24
        }}>
          <div style={{ fontSize: 36, marginBottom: 8 }}>📡</div>
          <div style={{ fontSize: 18, fontWeight: 700, color: "var(--text-primary)", marginBottom: 6 }}>
            Spectra Telemetry Standby
          </div>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", maxWidth: 520, margin: "0 auto 20px auto", lineHeight: 1.5 }}>
            No automatic dummy data is ingested on login. Telemetry pipelines are in clean standby mode. Upload a real-world microservice dataset or submit manual telemetry observations to begin monitoring.
          </p>
          <div style={{ display: "flex", justifyContent: "center", gap: 12 }}>
            <Link
              to="/ingestion"
              style={{
                padding: "8px 18px",
                backgroundColor: "var(--accent-primary)",
                color: "#fff",
                borderRadius: "var(--radius-sm)",
                fontSize: 13,
                fontWeight: 600,
                textDecoration: "none"
              }}
            >
              Ingest Telemetry CSV
            </Link>
            <Link
              to="/services"
              style={{
                padding: "8px 18px",
                backgroundColor: "var(--bg-subtle)",
                border: "1px solid var(--border-default)",
                color: "var(--text-primary)",
                borderRadius: "var(--radius-sm)",
                fontSize: 13,
                fontWeight: 600,
                textDecoration: "none"
              }}
            >
              Register Service
            </Link>
          </div>
        </div>
      )}

      {/* Enterprise Monitored Services Table for Admin */}
      {isAdmin && data.services.length > 0 && (
        <Card
          title="Enterprise Monitored Services"
          subtitle="All registered services across organization workspaces (Read-only monitoring)"
          actions={<Link to="/services" style={{ fontSize: 12 }}>View Services Table →</Link>}
          style={{ marginBottom: 24 }}
        >
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
            <input
              type="text"
              className="form-input"
              placeholder="Search services or owners..."
              value={serviceSearch}
              onChange={(e) => setServiceSearch(e.target.value)}
              style={{ maxWidth: 280 }}
            />

            <select
              className="form-select"
              value={ownerFilter}
              onChange={(e) => setOwnerFilter(e.target.value)}
              style={{ width: 220 }}
            >
              <option value="ALL">All Workspaces / Owners</option>
              {uniqueOwners.map((owner) => (
                <option key={owner} value={owner}>
                  {owner}
                </option>
              ))}
            </select>

            <select
              className="form-select"
              value={envFilter}
              onChange={(e) => setEnvFilter(e.target.value)}
              style={{ width: 160 }}
            >
              <option value="ALL">All Environments</option>
              <option value="DEVELOPMENT">DEVELOPMENT</option>
              <option value="STAGING">STAGING</option>
              <option value="PRODUCTION">PRODUCTION</option>
            </select>

            <select
              className="form-select"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              style={{ width: 140 }}
            >
              <option value="ALL">All Status</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="INACTIVE">INACTIVE</option>
            </select>

            <Button
              variant="secondary"
              onClick={loadDashboard}
              loading={loading}
              disabled={loading}
              style={{ marginLeft: "auto" }}
            >
              Refresh
            </Button>
          </div>

          {loading && data.services.length === 0 ? (
            <LoadingSpinner message="Loading enterprise services..." />
          ) : filteredEnterpriseServices.length === 0 ? (
            <EmptyState
              title="No services match criteria"
              description="Adjust the filters or search term above to inspect monitored services."
            />
          ) : (
            <div className="table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>Service</th>
                    <th>Workspace Owner</th>
                    <th>Environment</th>
                    <th>Endpoint</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredEnterpriseServices.map((svc) => (
                    <tr key={svc.id}>
                      <td>
                        <div style={{ fontWeight: 600 }}>{svc.name}</div>
                        {svc.description && (
                          <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 2 }}>
                            {svc.description}
                          </div>
                        )}
                      </td>
                      <td>
                        <code style={{ fontSize: 12, color: "var(--text-primary)" }}>
                          {svc.ownerEmail || "System"}
                        </code>
                      </td>
                      <td>
                        <span style={{ fontSize: 12, fontWeight: 500, color: "var(--text-secondary)" }}>
                          {svc.environment}
                        </span>
                      </td>
                      <td>
                        <code style={{ fontSize: 12, color: "var(--text-muted)" }}>{svc.endpointUrl}</code>
                      </td>
                      <td>
                        <Badge variant={svc.active ? "healthy" : "neutral"}>
                          {svc.active ? "ACTIVE" : "INACTIVE"}
                        </Badge>
                      </td>
                      <td>
                        <div style={{ display: "flex", gap: 10 }}>
                          <Link
                            to={`/metrics?serviceId=${svc.id}`}
                            style={{ fontSize: 12, color: "var(--accent-primary)", fontWeight: 500 }}
                          >
                            Telemetry
                          </Link>
                          <Link
                            to="/services"
                            style={{ fontSize: 12, color: "var(--text-secondary)", fontWeight: 500 }}
                          >
                            Details
                          </Link>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}

      {/* Telemetry and Alert Stream */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 460px), 1fr))", gap: 20, alignItems: "start" }}>
        <Card
          title="Recent Ingested Telemetry"
          subtitle={isAdmin ? "Latest observations recorded across all monitored services" : "Latest observations recorded in your workspace"}
          actions={<Link to="/metrics" style={{ fontSize: 12 }}>View all metrics →</Link>}
        >
          {loading && data.metrics.length === 0 ? (
            <LoadingSpinner message="Fetching telemetry..." />
          ) : errors.metrics ? (
            <div style={{ color: "var(--status-critical)", fontSize: 12, padding: 12 }}>{errors.metrics}</div>
          ) : data.metrics.length === 0 ? (
            <EmptyState title="No metrics ingested yet" description="Submit telemetry via the Ingestion page or API." />
          ) : (
            <div className="table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>Service</th>
                    <th>Metric</th>
                    <th>Value</th>
                    <th>Recorded At</th>
                  </tr>
                </thead>
                <tbody>
                  {data.metrics.map((m) => (
                    <tr key={m.id}>
                      <td style={{ fontWeight: 500 }}>{m.serviceName}</td>
                      <td><code>{m.metricName}</code></td>
                      <td>{m.metricValue} <span style={{ fontSize: 11, color: "var(--text-secondary)" }}>{m.unit}</span></td>
                      <td style={{ color: "var(--text-muted)", fontSize: 12 }}>
                        {new Date(m.recordedAt).toLocaleTimeString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>

        <Card
          title="Active Alerts & Incidents"
          subtitle={isAdmin ? "Unresolved alerts across all workspaces" : "Unresolved alerts requiring operational attention"}
          actions={<Link to="/alerts" style={{ fontSize: 12 }}>View all alerts →</Link>}
        >
          {loading && unresolvedAlerts.length === 0 ? (
            <LoadingSpinner message="Checking active alerts..." />
          ) : errors.alerts ? (
            <div style={{ color: "var(--status-critical)", fontSize: 12, padding: 12 }}>{errors.alerts}</div>
          ) : unresolvedAlerts.length === 0 ? (
            <EmptyState title="No active alerts" description="All alert rules are currently within normal thresholds." />
          ) : (
            <div className="table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>Severity</th>
                    <th>Rule / Service</th>
                    <th>Status</th>
                    <th>Triggered</th>
                  </tr>
                </thead>
                <tbody>
                  {unresolvedAlerts.slice(0, 6).map((a) => (
                    <tr key={a.id}>
                      <td><Badge>{a.severity}</Badge></td>
                      <td>
                        <div style={{ fontWeight: 500 }}>{a.ruleName}</div>
                        <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>
                          {a.serviceName} {isAdmin && a.ownerEmail ? `(${a.ownerEmail})` : ""}
                        </div>
                      </td>
                      <td><Badge>{a.status}</Badge></td>
                      <td style={{ color: "var(--text-muted)", fontSize: 12 }}>
                        {new Date(a.triggeredAt).toLocaleTimeString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
