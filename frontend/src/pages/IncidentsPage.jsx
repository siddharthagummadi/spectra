import React, { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { getAllIncidents, createIncident } from "../api/incidentsApi";
import { getAllServices } from "../api/servicesApi";
import { getAllAlerts } from "../api/alertsApi";
import { useAuth } from "../context/AuthContext";
import { Card } from "../components/common/Card";
import { Badge } from "../components/common/Badge";
import { Button } from "../components/common/Button";
import { Modal } from "../components/common/Modal";
import { AlertBanner } from "../components/common/AlertBanner";
import { LoadingSpinner } from "../components/common/LoadingSpinner";
import { EmptyState } from "../components/common/EmptyState";

export function IncidentsPage() {
  const [incidents, setIncidents] = useState([]);
  const [services, setServices] = useState([]);
  const [activeAlerts, setActiveAlerts] = useState([]);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");

  const [searchParams, setSearchParams] = useSearchParams();
  const { user, isAdmin } = useAuth();

  const [modalOpen, setModalOpen] = useState(false);
  const [modalLoading, setModalLoading] = useState(false);
  const [modalError, setModalError] = useState("");
  const [modalFieldErrors, setModalFieldErrors] = useState(null);
  const [incidentForm, setIncidentForm] = useState({
    mode: "manual",
    serviceId: "",
    alertId: "",
    title: "",
    description: "",
    severity: "HIGH",
    assignedTo: "",
    author: user?.email || "Operator"
  });

  const isRefreshingRef = useRef(false);

  const loadData = useCallback(async () => {
    if (isRefreshingRef.current) return;
    isRefreshingRef.current = true;
    setLoading(true);
    setErrorMessage("");
    try {
      const [incidentsData, servicesData, alertsData] = await Promise.all([
        getAllIncidents(statusFilter === "ALL" ? null : statusFilter),
        getAllServices(),
        getAllAlerts()
      ]);
      setIncidents(incidentsData || []);
      setServices(servicesData || []);
      const openAlerts = (alertsData || []).filter((a) => a.status !== "RESOLVED");
      setActiveAlerts(openAlerts);

      const alertParam = searchParams.get("createAlertId");
      if (alertParam) {
        const found = openAlerts.find((a) => String(a.id) === alertParam);
        setIncidentForm({
          mode: "alert",
          serviceId: found ? String(found.serviceId) : "",
          alertId: alertParam,
          title: found ? `Incident: ${found.ruleName}` : "",
          description: found?.message || "",
          severity: found?.severity || "HIGH",
          assignedTo: "",
          author: user?.email || "Operator"
        });
        setModalOpen(true);
        setSearchParams({});
      }
    } catch (err) {
      setErrorMessage(err.message || "Failed to load incidents");
    } finally {
      setLoading(false);
      isRefreshingRef.current = false;
    }
  }, [statusFilter, searchParams, setSearchParams, user]);

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

  const ownedAlerts = useMemo(() => {
    return activeAlerts.filter((a) => !isAdmin || !a.ownerEmail || a.ownerEmail === user?.email);
  }, [activeAlerts, isAdmin, user?.email]);

  const handleOpenCreate = () => {
    setIncidentForm({
      mode: "manual",
      serviceId: ownedServices[0]?.id ? String(ownedServices[0].id) : "",
      alertId: "",
      title: "",
      description: "",
      severity: "HIGH",
      assignedTo: "",
      author: user?.email || "Operator"
    });
    setModalError("");
    setModalFieldErrors(null);
    setModalOpen(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setModalLoading(true);
    setModalError("");
    setModalFieldErrors(null);

    try {
      const payload = {
        title: incidentForm.title,
        description: incidentForm.description || null,
        assignedTo: incidentForm.assignedTo || null,
        author: incidentForm.author || user?.email || "Operator"
      };

      if (incidentForm.mode === "alert" && incidentForm.alertId) {
        payload.alertId = Number(incidentForm.alertId);
        if (incidentForm.serviceId) payload.serviceId = Number(incidentForm.serviceId);
        if (incidentForm.severity) payload.severity = incidentForm.severity;
      } else {
        payload.serviceId = Number(incidentForm.serviceId);
        payload.severity = incidentForm.severity;
      }

      await createIncident(payload);
      setModalOpen(false);
      loadData();
    } catch (err) {
      setModalError(err.message || "Failed to create incident");
      setModalFieldErrors(err.fieldErrors || null);
    } finally {
      setModalLoading(false);
    }
  };

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20 }}>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 700 }}>Incident Management</h1>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 2 }}>
            {isAdmin
              ? "Enterprise-wide service incidents and investigation tracking"
              : "Track, investigate, and resolve operational service incidents"}
          </p>
        </div>
        <div style={{ display: "flex", gap: 10 }}>
          <Button variant="secondary" onClick={loadData} loading={loading} disabled={loading}>
            Refresh
          </Button>
          <Button variant="primary" onClick={handleOpenCreate} disabled={ownedServices.length === 0}>
            + Create Incident
          </Button>
        </div>
      </div>

      {errorMessage && (
        <AlertBanner message={errorMessage} onClose={() => setErrorMessage("")} />
      )}

      <Card>
        <div style={{ display: "flex", gap: 12, marginBottom: 16, alignItems: "center" }}>
          <label style={{ fontSize: 13, fontWeight: 500, color: "var(--text-secondary)" }}>
            Filter by Status:
          </label>
          <select
            className="form-select"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            style={{ width: 220 }}
          >
            <option value="ALL">All Incidents</option>
            <option value="OPEN">OPEN</option>
            <option value="INVESTIGATING">INVESTIGATING</option>
            <option value="RESOLVED">RESOLVED</option>
            <option value="CLOSED">CLOSED</option>
          </select>
        </div>

        {loading ? (
          <LoadingSpinner message="Loading incidents..." />
        ) : incidents.length === 0 ? (
          <EmptyState
            title="No incidents found"
            description={services.length === 0
              ? "Register a service first before creating manual incidents."
              : "Create an incident manually or link one from an active alert."}
            action={ownedServices.length > 0 ? (
              <Button variant="primary" onClick={handleOpenCreate}>Create Incident</Button>
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
                <th>ID</th>
                <th>Severity</th>
                <th>Title / Service</th>
                {isAdmin && <th>Owner</th>}
                <th>Status</th>
                <th>Assigned To</th>
                <th>Triggered At</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {incidents.map((inc) => (
                <tr key={inc.id}>
                  <td style={{ color: "var(--text-muted)", fontSize: 12 }}>#{inc.id}</td>
                  <td><Badge>{inc.severity}</Badge></td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{inc.title}</div>
                    <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                      {inc.serviceName} {inc.alertId && `(Alert #${inc.alertId})`}
                    </div>
                  </td>
                  {isAdmin && (
                    <td>
                      <code style={{ fontSize: 12, color: "var(--text-primary)" }}>
                        {inc.ownerEmail || "System"}
                      </code>
                    </td>
                  )}
                  <td><Badge>{inc.status}</Badge></td>
                  <td style={{ fontSize: 13 }}>{inc.assignedTo || "Unassigned"}</td>
                  <td style={{ color: "var(--text-muted)", fontSize: 12 }}>
                    {new Date(inc.triggeredAt).toLocaleString()}
                  </td>
                  <td>
                    <Link
                      to={`/incidents/${inc.id}`}
                      style={{
                        color: "var(--accent-primary)",
                        fontWeight: 600,
                        fontSize: 12
                      }}
                    >
                      Manage →
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <Modal
        isOpen={modalOpen}
        title="Create Incident"
        onClose={() => setModalOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button variant="primary" loading={modalLoading} onClick={handleSubmit}>
              Create Incident
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

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">Creation Mode</label>
            <div style={{ display: "flex", gap: 16 }}>
              <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 13, cursor: "pointer" }}>
                <input
                  type="radio"
                  name="mode"
                  value="manual"
                  checked={incidentForm.mode === "manual"}
                  onChange={() => setIncidentForm({ ...incidentForm, mode: "manual", alertId: "" })}
                />
                Manual Incident
              </label>
              <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 13, cursor: "pointer" }}>
                <input
                  type="radio"
                  name="mode"
                  value="alert"
                  checked={incidentForm.mode === "alert"}
                  onChange={() => setIncidentForm({ ...incidentForm, mode: "alert" })}
                  disabled={ownedAlerts.length === 0}
                />
                From Active Alert ({ownedAlerts.length})
              </label>
            </div>
          </div>

          {incidentForm.mode === "alert" ? (
            <div className="form-group">
              <label className="form-label" htmlFor="inc-alert">Select Active Alert *</label>
              <select
                id="inc-alert"
                className="form-select"
                value={incidentForm.alertId}
                onChange={(e) => {
                  const aid = e.target.value;
                  const found = ownedAlerts.find((a) => String(a.id) === aid);
                  setIncidentForm({
                    ...incidentForm,
                    alertId: aid,
                    serviceId: found ? String(found.serviceId) : "",
                    title: found ? `Incident: ${found.ruleName}` : incidentForm.title,
                    description: found?.message || incidentForm.description,
                    severity: found?.severity || "HIGH"
                  });
                }}
                required
              >
                <option value="">-- Choose active alert --</option>
                {ownedAlerts.map((a) => (
                  <option key={a.id} value={a.id}>
                    #{a.id} - {a.ruleName} ({a.serviceName}, {a.severity})
                  </option>
                ))}
              </select>
            </div>
          ) : (
            <div className="form-group">
              <label className="form-label" htmlFor="inc-svc">Target Service *</label>
              <select
                id="inc-svc"
                className="form-select"
                value={incidentForm.serviceId}
                onChange={(e) => setIncidentForm({ ...incidentForm, serviceId: e.target.value })}
                required
              >
                {ownedServices.map((s) => (
                  <option key={s.id} value={s.id}>{s.name} ({s.environment})</option>
                ))}
              </select>
            </div>
          )}

          <div className="form-group">
            <label className="form-label" htmlFor="inc-title">Incident Title *</label>
            <input
              id="inc-title"
              type="text"
              className="form-input"
              value={incidentForm.title}
              onChange={(e) => setIncidentForm({ ...incidentForm, title: e.target.value })}
              required
              maxLength={150}
              placeholder="e.g. Critical Latency Spike on Payment API"
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="inc-desc">Description</label>
            <textarea
              id="inc-desc"
              className="form-textarea"
              value={incidentForm.description}
              onChange={(e) => setIncidentForm({ ...incidentForm, description: e.target.value })}
              rows={3}
              placeholder="Details on symptoms, affected users, or trigger context..."
            />
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
            <div className="form-group">
              <label className="form-label" htmlFor="inc-sev">Severity *</label>
              <select
                id="inc-sev"
                className="form-select"
                value={incidentForm.severity}
                onChange={(e) => setIncidentForm({ ...incidentForm, severity: e.target.value })}
                required
                disabled={incidentForm.mode === "alert"}
              >
                <option value="LOW">LOW</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="HIGH">HIGH</option>
                <option value="CRITICAL">CRITICAL</option>
              </select>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="inc-assign">Assigned Engineer</label>
              <input
                id="inc-assign"
                type="text"
                className="form-input"
                value={incidentForm.assignedTo}
                onChange={(e) => setIncidentForm({ ...incidentForm, assignedTo: e.target.value })}
                placeholder="e.g. oncall-lead@company.local"
              />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="inc-author">Author / Reporter *</label>
            <input
              id="inc-author"
              type="text"
              className="form-input"
              value={incidentForm.author}
              onChange={(e) => setIncidentForm({ ...incidentForm, author: e.target.value })}
              required
            />
          </div>
        </form>
      </Modal>
    </div>
  );
}
