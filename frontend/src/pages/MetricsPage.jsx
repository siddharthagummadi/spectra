import React, { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { getRecentMetrics, getMetricsByServiceId, submitMetric } from "../api/metricsApi";
import { getAllServices } from "../api/servicesApi";
import { Card } from "../components/common/Card";
import { Button } from "../components/common/Button";
import { Modal } from "../components/common/Modal";
import { AlertBanner } from "../components/common/AlertBanner";
import { LoadingSpinner } from "../components/common/LoadingSpinner";
import { EmptyState } from "../components/common/EmptyState";

export function MetricsPage() {
  const { user, isAdmin } = useAuth();
  const [metrics, setMetrics] = useState([]);
  const [services, setServices] = useState([]);
  const [selectedServiceId, setSelectedServiceId] = useState("ALL");
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  const [modalOpen, setModalOpen] = useState(false);
  const [modalLoading, setModalLoading] = useState(false);
  const [modalError, setModalError] = useState("");
  const [modalFieldErrors, setModalFieldErrors] = useState(null);
  const [formData, setFormData] = useState({
    serviceId: "",
    metricName: "CPU_USAGE",
    metricValue: "",
    unit: "PERCENT"
  });

  const isRefreshingRef = useRef(false);

  const loadData = useCallback(async () => {
    if (isRefreshingRef.current) return;
    isRefreshingRef.current = true;
    setLoading(true);
    setErrorMessage("");
    try {
      const [servicesData, metricsData] = await Promise.all([
        getAllServices(),
        selectedServiceId === "ALL"
          ? getRecentMetrics(50)
          : getMetricsByServiceId(Number(selectedServiceId), 50)
      ]);
      setServices(servicesData || []);
      setMetrics(metricsData || []);
    } catch (err) {
      setErrorMessage(err.message || "Failed to load metrics");
    } finally {
      setLoading(false);
      isRefreshingRef.current = false;
    }
  }, [selectedServiceId]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    const handleGlobalRefresh = () => {
      loadData();
    };
    window.addEventListener("spectra:refresh", handleGlobalRefresh);
    return () => window.removeEventListener("spectra:refresh", handleGlobalRefresh);
  }, [loadData]);

  const ownedServices = useMemo(() => {
    return services.filter((s) => !isAdmin || !s.ownerEmail || s.ownerEmail === user?.email);
  }, [services, isAdmin, user?.email]);

  const handleMetricNameChange = (name) => {
    let unit = "PERCENT";
    if (name === "RESPONSE_TIME") unit = "MILLISECONDS";
    if (name === "MEMORY_USAGE") unit = "PERCENT";
    setFormData({ ...formData, metricName: name, unit });
  };

  const handleOpenIngest = () => {
    setFormData({
      serviceId: ownedServices[0]?.id ? String(ownedServices[0].id) : "",
      metricName: "CPU_USAGE",
      metricValue: "",
      unit: "PERCENT"
    });
    setModalError("");
    setModalFieldErrors(null);
    setModalOpen(true);
  };

  const handleSubmitIngest = async (e) => {
    e.preventDefault();
    setModalLoading(true);
    setModalError("");
    setModalFieldErrors(null);

    try {
      const payload = {
        serviceId: Number(formData.serviceId),
        metricName: formData.metricName,
        metricValue: Number(formData.metricValue),
        unit: formData.unit
      };
      await submitMetric(payload);
      setModalOpen(false);
      loadData();
    } catch (err) {
      setModalError(err.message || "Failed to submit metric");
      setModalFieldErrors(err.fieldErrors || null);
    } finally {
      setModalLoading(false);
    }
  };

  const chartData = [...metrics]
    .slice(0, 20)
    .reverse();

  const minVal = chartData.length > 0 ? Math.min(...chartData.map((d) => d.metricValue)) : 0;
  const maxVal = chartData.length > 0 ? Math.max(...chartData.map((d) => d.metricValue)) : 100;
  const range = maxVal - minVal === 0 ? 1 : maxVal - minVal;

  const points = chartData
    .map((d, idx) => {
      const x = 30 + (idx / Math.max(chartData.length - 1, 1)) * 540;
      const y = 130 - ((d.metricValue - minVal) / range) * 100;
      return `${x},${y}`;
    })
    .join(" ");

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20 }}>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 700 }}>Telemetry & Metrics</h1>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 2 }}>
            {isAdmin
              ? "Enterprise-wide telemetry stream and historical service measurements"
              : "Real-time measurement ingestion and historical service telemetry"}
          </p>
        </div>
        <div style={{ display: "flex", gap: 10 }}>
          <Button variant="secondary" onClick={loadData} loading={loading} disabled={loading}>
            Refresh
          </Button>
          <Button variant="primary" onClick={handleOpenIngest} disabled={ownedServices.length === 0}>
            + Submit Metric
          </Button>
        </div>
      </div>

      {errorMessage && (
        <AlertBanner message={errorMessage} onClose={() => setErrorMessage("")} />
      )}

      <div style={{ display: "grid", gridTemplateColumns: "1fr", gap: 20, marginBottom: 24 }}>
        <Card title="Telemetry Trend" subtitle="Recent 20 readings sparkline">
          {loading && metrics.length === 0 ? (
            <LoadingSpinner message="Loading telemetry trend..." />
          ) : chartData.length === 0 ? (
            <EmptyState
              title="No telemetry data"
              description="Submit measurements to visualize the telemetry trend."
            />
          ) : (
            <div style={{ width: "100%", overflowX: "auto" }}>
              <svg viewBox="0 0 600 160" style={{ width: "100%", height: 160 }}>
                <defs>
                  <linearGradient id="metricGrad" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--accent-primary)" stopOpacity="0.3" />
                    <stop offset="100%" stopColor="var(--accent-primary)" stopOpacity="0.0" />
                  </linearGradient>
                </defs>
                <line x1="30" y1="30" x2="570" y2="30" stroke="var(--border-subtle)" strokeDasharray="3 3" />
                <line x1="30" y1="80" x2="570" y2="80" stroke="var(--border-subtle)" strokeDasharray="3 3" />
                <line x1="30" y1="130" x2="570" y2="130" stroke="var(--border-subtle)" strokeDasharray="3 3" />
                <text x="5" y="34" fontSize="10" fill="var(--text-muted)">{Math.round(maxVal)}</text>
                <text x="5" y="134" fontSize="10" fill="var(--text-muted)">{Math.round(minVal)}</text>
                {points && (
                  <>
                    <polygon
                      points={`30,130 ${points} 570,130`}
                      fill="url(#metricGrad)"
                    />
                    <polyline
                      fill="none"
                      stroke="var(--accent-primary)"
                      strokeWidth="2.5"
                      points={points}
                    />
                  </>
                )}
              </svg>
            </div>
          )}
        </Card>
      </div>

      <Card>
        <div style={{ display: "flex", gap: 12, marginBottom: 16, alignItems: "center" }}>
          <label style={{ fontSize: 13, fontWeight: 500, color: "var(--text-secondary)" }}>
            Filter by Service:
          </label>
          <select
            className="form-select"
            value={selectedServiceId}
            onChange={(e) => setSelectedServiceId(e.target.value)}
            style={{ width: 260 }}
          >
            <option value="ALL">All Services (Latest Ingested)</option>
            {services.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name} ({s.environment}){isAdmin && s.ownerEmail ? ` - ${s.ownerEmail}` : ""}
              </option>
            ))}
          </select>
        </div>

        {loading ? (
          <LoadingSpinner message="Loading telemetry..." />
        ) : metrics.length === 0 ? (
          <EmptyState
            title="No metrics found"
            description="Ingest telemetry via the API or use the '+ Submit Metric' button above."
            action={ownedServices.length > 0 ? (
              <Button variant="primary" onClick={handleOpenIngest}>Submit Metric</Button>
            ) : (
              <Link to="/services">
                <Button variant="primary">Register Service</Button>
              </Link>
            )}
          />
        ) : (
          <table>
            <thead>
              <tr>
                <th>Service</th>
                <th>Metric Name</th>
                <th>Value</th>
                <th>Recorded At</th>
              </tr>
            </thead>
            <tbody>
              {metrics.map((m) => (
                <tr key={m.id}>
                  <td>
                    <div style={{ fontWeight: 600 }}>{m.serviceName}</div>
                  </td>
                  <td><code>{m.metricName}</code></td>
                  <td>
                    <span style={{ fontWeight: 600, fontSize: 14 }}>{m.metricValue}</span>{" "}
                    <span style={{ fontSize: 11, color: "var(--text-secondary)" }}>{m.unit}</span>
                  </td>
                  <td style={{ color: "var(--text-muted)", fontSize: 12 }}>
                    {new Date(m.recordedAt).toLocaleString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <Modal
        isOpen={modalOpen}
        title="Submit Service Metric"
        onClose={() => setModalOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button variant="primary" loading={modalLoading} onClick={handleSubmitIngest}>
              Submit Metric
            </Button>
          </>
        }
      >
        {modalError && (
          <AlertBanner
            variant="error"
            message={modalError}
            fieldErrors={modalFieldErrors}
            onClose={() => setModalError("")}
          />
        )}

        <form onSubmit={handleSubmitIngest}>
          <div className="form-group">
            <label className="form-label" htmlFor="metric-svc">Target Service *</label>
            <select
              id="metric-svc"
              className="form-select"
              value={formData.serviceId}
              onChange={(e) => setFormData({ ...formData, serviceId: e.target.value })}
              required
            >
              {ownedServices.map((s) => (
                <option key={s.id} value={s.id}>{s.name} ({s.environment})</option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="metric-name">Metric Name *</label>
            <select
              id="metric-name"
              className="form-select"
              value={formData.metricName}
              onChange={(e) => handleMetricNameChange(e.target.value)}
              required
            >
              <option value="CPU_USAGE">CPU_USAGE</option>
              <option value="MEMORY_USAGE">MEMORY_USAGE</option>
              <option value="RESPONSE_TIME">RESPONSE_TIME</option>
              <option value="ERROR_RATE">ERROR_RATE</option>
            </select>
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="metric-val">Value *</label>
            <input
              id="metric-val"
              type="number"
              step="any"
              min="0"
              className="form-input"
              value={formData.metricValue}
              onChange={(e) => setFormData({ ...formData, metricValue: e.target.value })}
              required
              placeholder="e.g. 75.5"
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="metric-unit">Unit *</label>
            {formData.metricName === "MEMORY_USAGE" ? (
              <select
                id="metric-unit"
                className="form-select"
                value={formData.unit}
                onChange={(e) => setFormData({ ...formData, unit: e.target.value })}
              >
                <option value="PERCENT">PERCENT</option>
                <option value="MEGABYTES">MEGABYTES</option>
              </select>
            ) : (
              <input
                id="metric-unit"
                type="text"
                className="form-input"
                value={formData.unit}
                disabled
              />
            )}
          </div>
        </form>
      </Modal>
    </div>
  );
}
