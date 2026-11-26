import React, { useState, useEffect, useCallback, useMemo } from "react";
import { getAiHealth, analyzeRootCause, getRunbooks, forecastTelemetry } from "../api/aiApi";
import { getAllServices } from "../api/servicesApi";
import { getAllIncidents } from "../api/incidentsApi";
import { getMetricsByServiceId } from "../api/metricsApi";
import { Card } from "../components/common/Card";
import { Badge } from "../components/common/Badge";
import { Button } from "../components/common/Button";
import { AlertBanner } from "../components/common/AlertBanner";
import { LoadingSpinner } from "../components/common/LoadingSpinner";
import { CapacityForecastCard } from "../components/analytics/CapacityForecastCard";

export function AiAnalyticsPage() {
  const [aiStatus, setAiStatus] = useState("CHECKING");
  const [aiDetails, setAiDetails] = useState(null);
  const [services, setServices] = useState([]);
  const [incidents, setIncidents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  // RCA State
  const [selectedIncidentId, setSelectedIncidentId] = useState("");
  const [rcaLoading, setRcaLoading] = useState(false);
  const [rcaResult, setRcaResult] = useState(null);
  const [rcaError, setRcaError] = useState("");

  // RAG Runbooks State
  const [runbookQuery, setRunbookQuery] = useState("JVM Heap Exhaustion");
  const [runbookTopK, setRunbookTopK] = useState(3);
  const [runbooksLoading, setRunbooksLoading] = useState(false);
  const [runbookResults, setRunbookResults] = useState([]);
  const [runbookError, setRunbookError] = useState("");

  // Initial Load
  const initPage = useCallback(async () => {
    setLoading(true);
    setErrorMessage("");
    try {
      const [healthRes, servicesRes, incidentsRes] = await Promise.allSettled([
        getAiHealth(),
        getAllServices(),
        getAllIncidents()
      ]);

      if (healthRes.status === "fulfilled") {
        setAiStatus(healthRes.value?.status === "UP" ? "ONLINE" : "DEGRADED");
        setAiDetails(healthRes.value);
      } else {
        setAiStatus("OFFLINE");
      }

      const svcList = servicesRes.status === "fulfilled" ? servicesRes.value || [] : [];
      const incList = incidentsRes.status === "fulfilled" ? incidentsRes.value || [] : [];

      setServices(svcList);
      setIncidents(incList);

      if (incList.length > 0) {
        setSelectedIncidentId(String(incList[0].id));
      }
    } catch (err) {
      setErrorMessage(err.message || "Failed to initialize AI analytics context");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    initPage();
  }, [initPage]);

  // Selected Incident Object
  const selectedIncident = useMemo(() => {
    if (!selectedIncidentId) return null;
    return incidents.find((inc) => String(inc.id) === selectedIncidentId) || null;
  }, [selectedIncidentId, incidents]);

  // Execute RCA
  const handleRunRca = async () => {
    if (!selectedIncident) return;
    setRcaLoading(true);
    setRcaError("");
    setRcaResult(null);

    try {
      let recentMetrics = [];
      if (selectedIncident.serviceId) {
        try {
          recentMetrics = await getMetricsByServiceId(selectedIncident.serviceId, 15);
        } catch {
          recentMetrics = [];
        }
      }

      const logs = selectedIncident.description ? [selectedIncident.description] : [];

      const res = await analyzeRootCause({
        serviceId: selectedIncident.serviceId,
        serviceName: selectedIncident.serviceName || "Service",
        incidentTitle: selectedIncident.title,
        incidentDescription: selectedIncident.description,
        recentMetrics: recentMetrics.map((m) => ({
          metricName: m.metricName,
          metricValue: m.metricValue
        })),
        activeAlerts: [],
        logs: logs
      });

      setRcaResult(res);

      // Auto-populate RAG query with root cause title
      if (res?.primary_root_cause) {
        setRunbookQuery(res.primary_root_cause);
      }
    } catch (err) {
      setRcaError(err.message || "Root cause analysis invocation failed");
    } finally {
      setRcaLoading(false);
    }
  };

  // Execute RAG Query
  const handleQueryRunbooks = async (overrideQuery) => {
    const q = (overrideQuery || runbookQuery || "").trim();
    setRunbooksLoading(true);
    setRunbookError("");

    try {
      const res = await getRunbooks(q, runbookTopK);
      const matches = res?.matches || res?.runbooks || [];
      setRunbookResults(matches);
    } catch (err) {
      setRunbookError(err.message || "Failed to retrieve runbooks from vector database");
    } finally {
      setRunbooksLoading(false);
    }
  };

  // Run initial query on page load if query exists
  useEffect(() => {
    if (runbookQuery && runbookResults.length === 0) {
      handleQueryRunbooks("JVM Heap Exhaustion");
    }
  }, []);

  if (loading) {
    return <LoadingSpinner text="Loading AI Operations Intelligence..." />;
  }

  return (
    <div style={{ maxWidth: 1280, margin: "0 auto", padding: "4px 0" }}>
      {/* Page Title & Status Header */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20, flexWrap: "wrap", gap: 14 }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <h1 style={{ fontSize: 22, fontWeight: 700, margin: 0, color: "var(--text-primary)" }}>
              AI Operations Analytics & Diagnostics
            </h1>
            <Badge variant={aiStatus === "ONLINE" ? "healthy" : aiStatus === "DEGRADED" ? "warning" : "critical"}>
              {aiStatus === "ONLINE" ? "FastAPI ML Service: Connected" : `AI Service: ${aiStatus}`}
            </Badge>
          </div>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 4, marginBottom: 0 }}>
            Automated Root Cause Analysis (RCA), TF-IDF RAG Remediation Runbooks, and Polynomial Telemetry Trend Forecasting.
          </p>
        </div>

        <Button variant="secondary" onClick={initPage}>
          ↻ Refresh Intelligence Context
        </Button>
      </div>

      {errorMessage && (
        <AlertBanner message={errorMessage} onClose={() => setErrorMessage("")} style={{ marginBottom: 16 }} />
      )}

      {/* Grid: Left Column = RCA & RAG, Right Column = Forecasting */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr", gap: 20 }}>

        {/* Section 1: Root Cause Analysis (RCA) */}
        <Card title="1. Automated Root Cause Analysis (Multi-Metric Signature Matching)" style={{ overflow: "hidden" }}>
          <div style={{ display: "flex", gap: 16, alignItems: "flex-end", marginBottom: 16, flexWrap: "wrap" }}>
            <div style={{ flex: 1, minWidth: 260 }}>
              <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--text-secondary)", marginBottom: 4 }}>
                Target Incident for Diagnostic Investigation:
              </label>
              <select
                value={selectedIncidentId}
                onChange={(e) => setSelectedIncidentId(e.target.value)}
                style={{
                  width: "100%",
                  padding: "8px 12px",
                  borderRadius: "var(--radius-sm)",
                  border: "1px solid var(--border-default)",
                  backgroundColor: "var(--bg-surface)",
                  color: "var(--text-primary)",
                  fontSize: 13
                }}
              >
                {incidents.map((inc) => (
                  <option key={inc.id} value={inc.id}>
                    #{inc.id} - [{inc.severity}] {inc.title} ({inc.serviceName})
                  </option>
                ))}
              </select>
            </div>

            <Button
              variant="primary"
              loading={rcaLoading}
              onClick={handleRunRca}
              style={{ padding: "8px 20px" }}
            >
              ⚡ Run Diagnostic RCA Engine
            </Button>
          </div>

          {selectedIncident && (
            <div style={{
              padding: "10px 14px",
              backgroundColor: "var(--bg-subtle)",
              borderRadius: "var(--radius-sm)",
              border: "1px solid var(--border-subtle)",
              fontSize: 12,
              marginBottom: 16
            }}>
              <div style={{ display: "flex", gap: 24, flexWrap: "wrap" }}>
                <div>
                  <span style={{ color: "var(--text-secondary)" }}>Service: </span>
                  <strong style={{ color: "var(--text-primary)" }}>{selectedIncident.serviceName}</strong>
                </div>
                <div>
                  <span style={{ color: "var(--text-secondary)" }}>Severity: </span>
                  <Badge variant={selectedIncident.severity === "CRITICAL" ? "critical" : "warning"}>
                    {selectedIncident.severity}
                  </Badge>
                </div>
                <div>
                  <span style={{ color: "var(--text-secondary)" }}>Status: </span>
                  <strong style={{ color: "var(--text-primary)" }}>{selectedIncident.status}</strong>
                </div>
              </div>
              <div style={{ marginTop: 6, color: "var(--text-secondary)" }}>
                <span>Telemetry Context: </span>
                <span style={{ fontStyle: "italic", color: "var(--text-primary)" }}>{selectedIncident.description}</span>
              </div>
            </div>
          )}

          {rcaError && (
            <AlertBanner message={rcaError} onClose={() => setRcaError("")} style={{ marginBottom: 14 }} />
          )}

          {rcaResult && (
            <div style={{
              padding: 16,
              borderRadius: "var(--radius-sm)",
              border: "1px solid var(--border-default)",
              backgroundColor: "var(--bg-surface)",
              marginTop: 10
            }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12, flexWrap: "wrap", gap: 8 }}>
                <div>
                  <span style={{ fontSize: 11, textTransform: "uppercase", color: "var(--text-secondary)", fontWeight: 600 }}>
                    Primary Root Cause Diagnosed
                  </span>
                  <div style={{ fontSize: 16, fontWeight: 700, color: "var(--status-critical)", marginTop: 2 }}>
                    {rcaResult.primary_root_cause}
                  </div>
                </div>

                <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
                  <Badge variant="warning">Category: {rcaResult.failure_category || "SYSTEM"}</Badge>
                  <div style={{
                    padding: "4px 10px",
                    borderRadius: "var(--radius-sm)",
                    backgroundColor: "var(--accent-light)",
                    color: "var(--accent-primary)",
                    fontSize: 12,
                    fontWeight: 700
                  }}>
                    Confidence: {(Number(rcaResult.confidence_score) * 100).toFixed(0)}%
                  </div>
                </div>
              </div>

              <div style={{ fontSize: 13, color: "var(--text-primary)", marginBottom: 10 }}>
                <strong>Diagnostic Evidence: </strong>
                <span>{rcaResult.primary_evidence || "Correlated metric breach signature with historical failure pattern."}</span>
              </div>

              {rcaResult.contributing_factors && rcaResult.contributing_factors.length > 0 && (
                <div style={{ marginTop: 10 }}>
                  <span style={{ fontSize: 11, color: "var(--text-secondary)", textTransform: "uppercase", fontWeight: 600 }}>
                    Contributing Telemetry Signatures:
                  </span>
                  <ul style={{ margin: "4px 0 0 0", paddingLeft: 18, fontSize: 12 }}>
                    {rcaResult.contributing_factors.map((factor, i) => (
                      <li key={i} style={{ color: "var(--text-secondary)", marginBottom: 2 }}>
                        {factor}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          )}
        </Card>

        {/* Section 2: Runbook Recommendations (RAG) */}
        <Card title="2. Retrieval-Augmented Generation (RAG) Runbook Knowledge Base">
          <div style={{ display: "flex", gap: 12, alignItems: "flex-end", marginBottom: 16, flexWrap: "wrap" }}>
            <div style={{ flex: 1, minWidth: 260 }}>
              <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--text-secondary)", marginBottom: 4 }}>
                Diagnostic Query / Incident Signature:
              </label>
              <input
                type="text"
                value={runbookQuery}
                onChange={(e) => setRunbookQuery(e.target.value)}
                placeholder="e.g. JVM Heap Exhaustion, Connection Pool Timeout, High CPU"
                style={{
                  width: "100%",
                  padding: "8px 12px",
                  borderRadius: "var(--radius-sm)",
                  border: "1px solid var(--border-default)",
                  backgroundColor: "var(--bg-surface)",
                  color: "var(--text-primary)",
                  fontSize: 13
                }}
              />
            </div>

            <div style={{ width: 110 }}>
              <label style={{ display: "block", fontSize: 12, fontWeight: 600, color: "var(--text-secondary)", marginBottom: 4 }}>
                Top Matches:
              </label>
              <select
                value={runbookTopK}
                onChange={(e) => setRunbookTopK(Number(e.target.value))}
                style={{
                  width: "100%",
                  padding: "8px 10px",
                  borderRadius: "var(--radius-sm)",
                  border: "1px solid var(--border-default)",
                  backgroundColor: "var(--bg-surface)",
                  color: "var(--text-primary)",
                  fontSize: 13
                }}
              >
                <option value={1}>Top 1</option>
                <option value={2}>Top 2</option>
                <option value={3}>Top 3</option>
                <option value={5}>Top 5</option>
              </select>
            </div>

            <Button
              variant="secondary"
              loading={runbooksLoading}
              onClick={() => handleQueryRunbooks()}
              style={{ padding: "8px 18px" }}
            >
              🔍 Query Runbooks
            </Button>
          </div>

          {runbookError && (
            <AlertBanner message={runbookError} onClose={() => setRunbookError("")} style={{ marginBottom: 14 }} />
          )}

          {runbookResults.length === 0 && !runbooksLoading ? (
            <div style={{ padding: 20, textAlign: "center", color: "var(--text-secondary)", fontSize: 13 }}>
              No matching runbooks found for query. Try searching for "Heap", "CPU", "Timeout", or "Error".
            </div>
          ) : (
            <div style={{ display: "grid", gridTemplateColumns: "1fr", gap: 12 }}>
              {runbookResults.map((rb, idx) => (
                <div
                  key={rb.runbook_id || idx}
                  style={{
                    padding: 14,
                    border: "1px solid var(--border-default)",
                    borderRadius: "var(--radius-sm)",
                    backgroundColor: "var(--bg-subtle)"
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 6 }}>
                    <div>
                      <div style={{ fontSize: 11, color: "var(--text-muted)", textTransform: "uppercase", fontWeight: 600 }}>
                        {rb.runbook_id || `RUNBOOK-${idx + 1}`} • Trigger: {rb.metric_trigger || "TELEMETRY"}
                      </div>
                      <strong style={{ fontSize: 14, color: "var(--text-primary)" }}>{rb.title}</strong>
                    </div>

                    <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                      <Badge variant="healthy">Cosine Match: {(Number(rb.score || 0) * 100).toFixed(1)}%</Badge>
                      <Badge variant="neutral">{rb.service_type || "BACKEND"}</Badge>
                    </div>
                  </div>

                  {rb.immediate_actions && rb.immediate_actions.length > 0 && (
                    <div style={{ marginTop: 8 }}>
                      <span style={{ fontSize: 11, color: "var(--text-secondary)", fontWeight: 600, display: "block", marginBottom: 4 }}>
                        Immediate Standard Operating Procedure (SOP):
                      </span>
                      <ol style={{ margin: 0, paddingLeft: 18, fontSize: 12, color: "var(--text-primary)" }}>
                        {rb.immediate_actions.map((act, actIdx) => (
                          <li key={actIdx} style={{ marginBottom: 2 }}>{act}</li>
                        ))}
                      </ol>
                    </div>
                  )}

                  {rb.long_term_fix && (
                    <div style={{ marginTop: 8, fontSize: 12, color: "var(--text-secondary)" }}>
                      <strong style={{ color: "var(--text-primary)" }}>Long-term Remediation: </strong>
                      <span>{rb.long_term_fix}</span>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* Section 3: Metric Trend Forecasting */}
        <div>
          <CapacityForecastCard services={services} />
        </div>

      </div>
    </div>
  );
}
