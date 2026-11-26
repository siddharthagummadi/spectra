import React, { useState, useEffect, useCallback } from "react";
import { Link } from "react-router-dom";
import {
  getAllEnterprises,
  createEnterprise,
  generateEnrollmentToken,
  updateEnterpriseStatus
} from "../api/adminApi";
import { statusBadgeVariant } from "./AdminOverviewPage";
import { Card } from "../components/common/Card";
import { Badge } from "../components/common/Badge";
import { Button } from "../components/common/Button";
import { Modal } from "../components/common/Modal";
import { AlertBanner } from "../components/common/AlertBanner";
import { LoadingSpinner } from "../components/common/LoadingSpinner";
import { EmptyState } from "../components/common/EmptyState";

export function AdminEnterprisesPage() {
  const [enterprises, setEnterprises] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [issuedToken, setIssuedToken] = useState(null);

  const [form, setForm] = useState({
    name: "",
    organizationCode: "",
    environment: "PRODUCTION",
    contactName: "",
    contactEmail: "",
    tokenTtlMinutes: 60
  });

  const loadEnterprises = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await getAllEnterprises();
      setEnterprises(data || []);
    } catch (err) {
      setError(err.message || "Failed to load enterprises");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadEnterprises();
  }, [loadEnterprises]);

  const handleConnectEnterprise = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      const res = await createEnterprise(form);
      setIssuedToken(res.enrollment);
      setModalOpen(false);
      setForm({
        name: "",
        organizationCode: "",
        environment: "PRODUCTION",
        contactName: "",
        contactEmail: "",
        tokenTtlMinutes: 60
      });
      await loadEnterprises();
    } catch (err) {
      setError(err.message || "Failed to create enterprise");
    } finally {
      setSubmitting(false);
    }
  };

  const handleRegenerateToken = async (enterpriseId) => {
    setError("");
    try {
      const tokenRes = await generateEnrollmentToken(enterpriseId, 60);
      setIssuedToken(tokenRes);
    } catch (err) {
      setError(err.message || "Failed to generate enrollment token");
    }
  };

  const handleStatusChange = async (enterpriseId, nextStatus) => {
    setError("");
    try {
      await updateEnterpriseStatus(enterpriseId, nextStatus, `Status set to ${nextStatus} by Platform Admin`);
      await loadEnterprises();
    } catch (err) {
      setError(err.message || "Failed to update enterprise status");
    }
  };

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20, flexWrap: "wrap", gap: 12 }}>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 700, margin: 0 }}>Enterprise Connections</h1>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 4 }}>
            Onboard enterprises, issue temporary single-use agent enrollment tokens, and govern connection states.
          </p>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <Button variant="primary" onClick={() => setModalOpen(true)}>+ Connect Enterprise</Button>
          <Button variant="secondary" onClick={loadEnterprises} loading={loading}>↻ Refresh</Button>
        </div>
      </div>

      {error && <AlertBanner message={error} onClose={() => setError("")} />}

      {issuedToken && (
        <Card
          title="Temporary Single-Use Agent Enrollment Token Issued"
          subtitle={`Enterprise: ${issuedToken.enterpriseName} (${issuedToken.organizationCode}) · Expires: ${new Date(issuedToken.expiresAt).toLocaleString()}`}
          actions={<Button variant="secondary" onClick={() => setIssuedToken(null)}>Dismiss</Button>}
          style={{ marginBottom: 20 }}
        >
          <div style={{ display: "grid", gap: 10, fontSize: 13 }}>
            <div>
              <strong>Enrollment Token (copy now — shown once):</strong>
              <pre style={{ backgroundColor: "var(--bg-subtle)", padding: 10, borderRadius: "var(--radius-sm)", overflowX: "auto", marginTop: 4 }}>
                {issuedToken.enrollmentToken}
              </pre>
            </div>
            <div>
              <strong>Standalone Agent Install &amp; Enrollment Command:</strong>
              <pre style={{ backgroundColor: "var(--bg-subtle)", padding: 10, borderRadius: "var(--radius-sm)", overflowX: "auto", marginTop: 4 }}>
                {issuedToken.installCommand}
              </pre>
            </div>
          </div>
        </Card>
      )}

      <Card title={`Registered Enterprises (${enterprises.length})`}>
        {loading && enterprises.length === 0 ? (
          <LoadingSpinner label="Loading enterprises..." />
        ) : enterprises.length === 0 ? (
          <EmptyState
            title="No Enterprises Connected"
            description="Click '+ Connect Enterprise' to start onboarding an enterprise infrastructure."
          />
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Enterprise</th>
                  <th>Org Code</th>
                  <th>Environment</th>
                  <th>Connection Status</th>
                  <th>Agents</th>
                  <th>Last Telemetry</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {enterprises.map((ent) => (
                  <tr key={ent.id}>
                    <td>
                      <div style={{ fontWeight: 600 }}>{ent.name}</div>
                      <div style={{ fontSize: 11, color: "var(--text-muted)" }}>{ent.contactEmail || "No contact email"}</div>
                    </td>
                    <td><code>{ent.organizationCode}</code></td>
                    <td>{ent.environment}</td>
                    <td>
                      <Badge variant={statusBadgeVariant(ent.connectionStatus)}>{ent.connectionStatus}</Badge>
                      {ent.statusReason && (
                        <div style={{ fontSize: 11, color: "var(--text-muted)", marginTop: 3 }}>{ent.statusReason}</div>
                      )}
                    </td>
                    <td>{ent.onlineAgentCount} / {ent.agentCount}</td>
                    <td style={{ fontSize: 12 }}>
                      {ent.lastTelemetryAt ? new Date(ent.lastTelemetryAt).toLocaleString() : "None"}
                    </td>
                    <td>
                      <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                        <Link to={`/admin/enterprises/${ent.id}`} style={{ textDecoration: "none" }}>
                          <Button variant="secondary" style={{ fontSize: 11, padding: "4px 8px" }}>Open Dashboard</Button>
                        </Link>
                        <Button
                          variant="secondary"
                          style={{ fontSize: 11, padding: "4px 8px" }}
                          onClick={() => handleRegenerateToken(ent.id)}
                        >
                          Issue Token
                        </Button>
                        {ent.connectionStatus !== "DISCONNECTED" ? (
                          <Button
                            variant="secondary"
                            style={{ fontSize: 11, padding: "4px 8px", color: "var(--status-critical)" }}
                            onClick={() => handleStatusChange(ent.id, "DISCONNECTED")}
                          >
                            Disconnect
                          </Button>
                        ) : (
                          <Button
                            variant="secondary"
                            style={{ fontSize: 11, padding: "4px 8px" }}
                            onClick={() => handleStatusChange(ent.id, "PENDING")}
                          >
                            Re-enable
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Modal
        isOpen={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Connect New Enterprise"
        footer={
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button variant="primary" loading={submitting} onClick={handleConnectEnterprise}>
              Create &amp; Issue Enrollment Token
            </Button>
          </div>
        }
      >
        <form onSubmit={handleConnectEnterprise}>
          <div className="form-group">
            <label className="form-label">Enterprise Name</label>
            <input
              className="form-input"
              required
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              placeholder="Acme Financial Corp"
            />
          </div>
          <div className="form-group">
            <label className="form-label">Organization Code</label>
            <input
              className="form-input"
              required
              value={form.organizationCode}
              onChange={(e) => setForm({ ...form, organizationCode: e.target.value.toUpperCase() })}
              placeholder="ACME_PROD"
            />
          </div>
          <div className="form-group">
            <label className="form-label">Environment</label>
            <select
              className="form-select"
              value={form.environment}
              onChange={(e) => setForm({ ...form, environment: e.target.value })}
            >
              <option value="PRODUCTION">PRODUCTION</option>
              <option value="STAGING">STAGING</option>
              <option value="DEVELOPMENT">DEVELOPMENT</option>
            </select>
          </div>
          <div className="form-group">
            <label className="form-label">Primary Contact Name</label>
            <input
              className="form-input"
              value={form.contactName}
              onChange={(e) => setForm({ ...form, contactName: e.target.value })}
              placeholder="SRE Lead"
            />
          </div>
          <div className="form-group">
            <label className="form-label">Primary Contact Email</label>
            <input
              type="email"
              className="form-input"
              value={form.contactEmail}
              onChange={(e) => setForm({ ...form, contactEmail: e.target.value })}
              placeholder="sre@acme.com"
            />
          </div>
        </form>
      </Modal>
    </div>
  );
}
