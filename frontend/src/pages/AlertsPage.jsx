import React, { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import {
  getAllRules,
  createRule,
  updateRuleStatus,
  deleteRule,
  getAllAlerts,
  acknowledgeAlert
} from "../api/alertsApi";
import { getAllServices } from "../api/servicesApi";
import { Card } from "../components/common/Card";
import { Badge } from "../components/common/Badge";
import { Button } from "../components/common/Button";
import { Modal } from "../components/common/Modal";
import { AlertBanner } from "../components/common/AlertBanner";
import { LoadingSpinner } from "../components/common/LoadingSpinner";
import { EmptyState } from "../components/common/EmptyState";

export function AlertsPage() {
  const { user, isAdmin } = useAuth();
  const [activeTab, setActiveTab] = useState("events");
  const [rules, setRules] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [services, setServices] = useState([]);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  const [modalOpen, setModalOpen] = useState(false);
  const [modalLoading, setModalLoading] = useState(false);
  const [modalError, setModalError] = useState("");
  const [modalFieldErrors, setModalFieldErrors] = useState(null);
  const [ruleForm, setRuleForm] = useState({
    serviceId: "",
    ruleName: "",
    metricName: "CPU_USAGE",
    unit: "PERCENT",
    comparator: "GREATER_THAN",
    thresholdValue: "",
    severity: "HIGH"
  });

  const isRefreshingRef = useRef(false);

  const loadData = useCallback(async () => {
    if (isRefreshingRef.current) return;
    isRefreshingRef.current = true;
    setLoading(true);
    setErrorMessage("");
    try {
      const [rulesData, alertsData, servicesData] = await Promise.all([
        getAllRules(),
        getAllAlerts(statusFilter === "ALL" ? null : statusFilter),
        getAllServices()
      ]);
      setRules(rulesData || []);
      setAlerts(alertsData || []);
      setServices(servicesData || []);
    } catch (err) {
      setErrorMessage(err.message || "Failed to load alerts data");
    } finally {
      setLoading(false);
      isRefreshingRef.current = false;
    }
  }, [statusFilter]);

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

  const handleOpenCreateRule = () => {
    setRuleForm({
      serviceId: ownedServices[0]?.id ? String(ownedServices[0].id) : "",
      ruleName: "",
      metricName: "CPU_USAGE",
      unit: "PERCENT",
      comparator: "GREATER_THAN",
      thresholdValue: "80",
      severity: "HIGH"
    });
    setModalError("");
    setModalFieldErrors(null);
    setModalOpen(true);
  };

  const handleMetricNameChange = (name) => {
    let unit = "PERCENT";
    if (name === "RESPONSE_TIME") unit = "MILLISECONDS";
    if (name === "MEMORY_USAGE") unit = "PERCENT";
    setRuleForm({ ...ruleForm, metricName: name, unit });
  };

  const handleSubmitRule = async (e) => {
    e.preventDefault();
    setModalLoading(true);
    setModalError("");
    setModalFieldErrors(null);

    try {
      const payload = {
        serviceId: Number(ruleForm.serviceId),
        ruleName: ruleForm.ruleName,
        metricName: ruleForm.metricName,
        unit: ruleForm.unit,
        comparator: ruleForm.comparator,
        thresholdValue: Number(ruleForm.thresholdValue),
        severity: ruleForm.severity
      };
      await createRule(payload);
      setModalOpen(false);
      loadData();
    } catch (err) {
      setModalError(err.message || "Failed to create rule");
      setModalFieldErrors(err.fieldErrors || null);
    } finally {
      setModalLoading(false);
    }
  };

  const handleToggleRuleStatus = async (rule) => {
    try {
      await updateRuleStatus(rule.id, !rule.active);
      loadData();
    } catch (err) {
      setErrorMessage(err.message || "Failed to update rule status");
    }
  };

  const handleDeleteRule = async (id) => {
    try {
      await deleteRule(id);
      loadData();
    } catch (err) {
      setErrorMessage(err.message || "Failed to delete alert rule");
    }
  };

  const handleAcknowledgeAlert = async (id) => {
    try {
      await acknowledgeAlert(id);
      loadData();
    } catch (err) {
      setErrorMessage(err.message || "Failed to acknowledge alert");
    }
  };

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20 }}>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 700 }}>Alert Management</h1>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 2 }}>
            {isAdmin
              ? "Enterprise-wide operational alert events and threshold breach notifications"
              : "Monitor operational alert events and threshold breach notifications"}
          </p>
        </div>
        <div style={{ display: "flex", gap: 10 }}>
          <Button variant="secondary" onClick={loadData} loading={loading} disabled={loading}>
            Refresh
          </Button>
          <Button variant="primary" onClick={handleOpenCreateRule} disabled={ownedServices.length === 0}>
            + Create Alert Rule
          </Button>
        </div>
      </div>

      <div style={{
        padding: "10px 14px",
        backgroundColor: "var(--bg-subtle)",
        border: "1px solid var(--border-subtle)",
        borderRadius: "var(--radius-sm)",
        marginBottom: 16,
        fontSize: 13,
        color: "var(--text-secondary)",
        display: "flex",
        alignItems: "center",
        gap: 8
      }}>
        <span>ℹ️</span>
        <span>
          Alerts are generated automatically when submitted telemetry metrics breach configured threshold rules for monitored services.
        </span>
      </div>

      {errorMessage && (
        <AlertBanner message={errorMessage} onClose={() => setErrorMessage("")} />
      )}

      <div style={{ display: "flex", borderBottom: "1px solid var(--border-default)", marginBottom: 16 }}>
        <button
          onClick={() => setActiveTab("events")}
          style={{
            padding: "8px 16px",
            background: "none",
            border: "none",
            borderBottom: activeTab === "events" ? "2px solid var(--accent-primary)" : "2px solid transparent",
            fontWeight: 600,
            fontSize: 13,
            color: activeTab === "events" ? "var(--accent-primary)" : "var(--text-secondary)",
            cursor: "pointer"
          }}
        >
          Alert Events ({alerts.length})
        </button>
        <button
          onClick={() => setActiveTab("rules")}
          style={{
            padding: "8px 16px",
            background: "none",
            border: "none",
            borderBottom: activeTab === "rules" ? "2px solid var(--accent-primary)" : "2px solid transparent",
            fontWeight: 600,
            fontSize: 13,
            color: activeTab === "rules" ? "var(--accent-primary)" : "var(--text-secondary)",
            cursor: "pointer"
          }}
        >
          Threshold Rules ({rules.length})
        </button>
      </div>

      {activeTab === "events" ? (
        <Card>
          <div style={{ display: "flex", gap: 12, marginBottom: 16, alignItems: "center" }}>
            <label style={{ fontSize: 13, fontWeight: 500, color: "var(--text-secondary)" }}>
              Filter by Status:
            </label>
            <select
              className="form-select"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              style={{ width: 200 }}
            >
              <option value="ALL">All Alert Events</option>
              <option value="TRIGGERED">TRIGGERED</option>
              <option value="ACKNOWLEDGED">ACKNOWLEDGED</option>
              <option value="RESOLVED">RESOLVED</option>
            </select>
          </div>

          {loading ? (
            <LoadingSpinner message="Loading alert events..." />
          ) : alerts.length === 0 ? (
            <EmptyState
              title="No alert events"
              description="Alert events trigger automatically when incoming telemetry breaches an active rule."
            />
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Severity</th>
                  <th>Status</th>
                  <th>Service / Rule</th>
                  {isAdmin && <th>Owner</th>}
                  <th>Breach Condition</th>
                  <th>Triggered At</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {alerts.map((a) => {
                  const isOwner = !isAdmin || !a.ownerEmail || a.ownerEmail === user?.email;
                  return (
                    <tr key={a.id}>
                      <td><Badge>{a.severity}</Badge></td>
                      <td><Badge>{a.status}</Badge></td>
                      <td>
                        <div style={{ fontWeight: 600 }}>{a.ruleName}</div>
                        <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>{a.serviceName}</div>
                      </td>
                      {isAdmin && (
                        <td>
                          <code style={{ fontSize: 12, color: "var(--text-primary)" }}>
                            {a.ownerEmail || "System"}
                          </code>
                        </td>
                      )}
                      <td>
                        <div>
                          Triggered: <strong>{a.triggerValue}</strong> (Threshold: {a.thresholdValue} {a.unit})
                        </div>
                        <div style={{ fontSize: 11, color: "var(--text-muted)", marginTop: 2 }}>{a.message}</div>
                      </td>
                      <td style={{ color: "var(--text-muted)", fontSize: 12 }}>
                        {new Date(a.triggeredAt).toLocaleString()}
                        {a.resolvedAt && (
                          <div style={{ color: "var(--status-healthy)", fontSize: 11, marginTop: 2 }}>
                            Resolved: {new Date(a.resolvedAt).toLocaleTimeString()}
                          </div>
                        )}
                      </td>
                      <td>
                        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                          {a.status === "TRIGGERED" && (
                            isOwner ? (
                              <button
                                onClick={() => handleAcknowledgeAlert(a.id)}
                                style={{
                                  background: "none",
                                  border: "none",
                                  color: "var(--status-warning)",
                                  fontSize: 12,
                                  fontWeight: 600,
                                  cursor: "pointer"
                                }}
                              >
                                Acknowledge
                              </button>
                            ) : (
                              <span style={{ fontSize: 12, color: "var(--text-muted)", fontStyle: "italic" }}>
                                Read-only
                              </span>
                            )
                          )}
                          {a.status !== "RESOLVED" && isOwner && (
                            <Link
                              to={`/incidents?createAlertId=${a.id}`}
                              style={{
                                color: "var(--accent-primary)",
                                fontSize: 12,
                                fontWeight: 500
                              }}
                            >
                              Link Incident
                            </Link>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </Card>
      ) : (
        <Card>
          {loading ? (
            <LoadingSpinner message="Loading alert rules..." />
          ) : rules.length === 0 ? (
            services.length === 0 ? (
              <EmptyState
                title="No monitored services found"
                description="You need to register at least one monitored service before configuring alert threshold rules."
                action={
                  <Link to="/services">
                    <Button variant="primary">Register Service</Button>
                  </Link>
                }
              />
            ) : (
              <EmptyState
                title="No alert rules defined"
                description="Define threshold rules for your services to automatically trigger operational alerts when metrics exceed healthy bounds."
                action={
                  <Button variant="primary" onClick={handleOpenCreateRule} disabled={ownedServices.length === 0}>
                    Create Alert Rule
                  </Button>
                }
              />
            )
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Rule Name</th>
                  <th>Service</th>
                  {isAdmin && <th>Owner</th>}
                  <th>Metric Condition</th>
                  <th>Severity</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {rules.map((r) => {
                  const isOwner = !isAdmin || !r.ownerEmail || r.ownerEmail === user?.email;
                  return (
                    <tr key={r.id}>
                      <td style={{ fontWeight: 600 }}>{r.ruleName}</td>
                      <td>{r.serviceName}</td>
                      {isAdmin && (
                        <td>
                          <code style={{ fontSize: 12, color: "var(--text-primary)" }}>
                            {r.ownerEmail || "System"}
                          </code>
                        </td>
                      )}
                      <td>
                        <code>{r.metricName}</code> {r.comparator} <strong>{r.thresholdValue}</strong> {r.unit}
                      </td>
                      <td><Badge>{r.severity}</Badge></td>
                      <td>
                        <Badge variant={r.active ? "healthy" : "neutral"}>
                          {r.active ? "ENABLED" : "DISABLED"}
                        </Badge>
                      </td>
                      <td>
                        {isOwner ? (
                          <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
                            <button
                              onClick={() => handleToggleRuleStatus(r)}
                              style={{
                                background: "none",
                                border: "none",
                                color: r.active ? "var(--status-warning)" : "var(--status-healthy)",
                                fontSize: 12,
                                fontWeight: 500,
                                cursor: "pointer"
                              }}
                            >
                              {r.active ? "Disable" : "Enable"}
                            </button>
                            <button
                              onClick={() => handleDeleteRule(r.id)}
                              style={{
                                background: "none",
                                border: "none",
                                color: "var(--status-critical)",
                                fontSize: 12,
                                fontWeight: 500,
                                cursor: "pointer"
                              }}
                            >
                              Delete
                            </button>
                          </div>
                        ) : (
                          <span style={{ fontSize: 12, color: "var(--text-muted)", fontStyle: "italic" }}>
                            Read-only
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </Card>
      )}

      <Modal
        isOpen={modalOpen}
        title="Create Alert Rule"
        onClose={() => setModalOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button variant="primary" loading={modalLoading} onClick={handleSubmitRule}>
              Save Rule
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

        <form onSubmit={handleSubmitRule}>
          <div className="form-group">
            <label className="form-label" htmlFor="rule-svc">Target Service *</label>
            <select
              id="rule-svc"
              className="form-select"
              value={ruleForm.serviceId}
              onChange={(e) => setRuleForm({ ...ruleForm, serviceId: e.target.value })}
              required
            >
              {ownedServices.map((s) => (
                <option key={s.id} value={s.id}>{s.name} ({s.environment})</option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="rule-name">Rule Name *</label>
            <input
              id="rule-name"
              type="text"
              className="form-input"
              value={ruleForm.ruleName}
              onChange={(e) => setRuleForm({ ...ruleForm, ruleName: e.target.value })}
              required
              maxLength={100}
              placeholder="e.g. CPU Critical Breach"
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="rule-metric">Metric Name *</label>
            <select
              id="rule-metric"
              className="form-select"
              value={ruleForm.metricName}
              onChange={(e) => handleMetricNameChange(e.target.value)}
              required
            >
              <option value="CPU_USAGE">CPU_USAGE</option>
              <option value="MEMORY_USAGE">MEMORY_USAGE</option>
              <option value="RESPONSE_TIME">RESPONSE_TIME</option>
              <option value="ERROR_RATE">ERROR_RATE</option>
            </select>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div className="form-group">
              <label className="form-label" htmlFor="rule-comp">Comparator *</label>
              <select
                id="rule-comp"
                className="form-select"
                value={ruleForm.comparator}
                onChange={(e) => setRuleForm({ ...ruleForm, comparator: e.target.value })}
                required
              >
                <option value="GREATER_THAN">GREATER_THAN (&gt;)</option>
                <option value="GREATER_THAN_OR_EQUAL">GREATER_THAN_OR_EQUAL (&gt;=)</option>
                <option value="LESS_THAN">LESS_THAN (&lt;)</option>
                <option value="LESS_THAN_OR_EQUAL">LESS_THAN_OR_EQUAL (&lt;=)</option>
              </select>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="rule-thresh">Threshold Value *</label>
              <input
                id="rule-thresh"
                type="number"
                step="any"
                min="0"
                className="form-input"
                value={ruleForm.thresholdValue}
                onChange={(e) => setRuleForm({ ...ruleForm, thresholdValue: e.target.value })}
                required
              />
            </div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div className="form-group">
              <label className="form-label" htmlFor="rule-unit">Unit *</label>
              {ruleForm.metricName === "MEMORY_USAGE" ? (
                <select
                  id="rule-unit"
                  className="form-select"
                  value={ruleForm.unit}
                  onChange={(e) => setRuleForm({ ...ruleForm, unit: e.target.value })}
                >
                  <option value="PERCENT">PERCENT</option>
                  <option value="MEGABYTES">MEGABYTES</option>
                </select>
              ) : (
                <input
                  id="rule-unit"
                  type="text"
                  className="form-input"
                  value={ruleForm.unit}
                  disabled
                />
              )}
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="rule-sev">Severity *</label>
              <select
                id="rule-sev"
                className="form-select"
                value={ruleForm.severity}
                onChange={(e) => setRuleForm({ ...ruleForm, severity: e.target.value })}
                required
              >
                <option value="LOW">LOW</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="HIGH">HIGH</option>
                <option value="CRITICAL">CRITICAL</option>
              </select>
            </div>
          </div>
        </form>
      </Modal>
    </div>
  );
}
