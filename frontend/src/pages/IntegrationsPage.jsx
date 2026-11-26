import React, { useState, useEffect } from "react";
import {
  createIntegrationRequest,
  getIntegrations,
  updateIntegrationStatus
} from "../api/integrationsApi";
import { Button } from "../components/common/Button";
import { Badge } from "../components/common/Badge";
import { AlertBanner } from "../components/common/AlertBanner";
import { useAuth } from "../context/AuthContext";

export function IntegrationsPage() {
  const { user, isAdmin } = useAuth();

  const [integrations, setIntegrations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  // Request Form State
  const [enterpriseName, setEnterpriseName] = useState("");
  const [organizationName, setOrganizationName] = useState("");
  const [environment, setEnvironment] = useState("PRODUCTION");
  const [integrationType, setIntegrationType] = useState("PROMETHEUS");
  const [apiEndpoint, setApiEndpoint] = useState("");
  const [contactName, setContactName] = useState("");
  const [contactEmail, setContactEmail] = useState(user?.email || "");
  const [connectionPurpose, setConnectionPurpose] = useState("");
  const [credentials, setCredentials] = useState("");
  const [submitting, setSubmitting] = useState(false);

  // Review Modal State (Admin)
  const [reviewItem, setReviewItem] = useState(null);
  const [reviewStatus, setReviewStatus] = useState("APPROVED");
  const [reviewReason, setReviewReason] = useState("");
  const [reviewing, setReviewing] = useState(false);

  const fetchIntegrations = async () => {
    setLoading(true);
    try {
      const data = await getIntegrations();
      setIntegrations(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.message || "Failed to load enterprise integrations");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchIntegrations();
  }, []);

  const handleSubmitRequest = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError("");
    setSuccessMessage("");

    try {
      const payload = {
        enterpriseName,
        organizationName,
        environment,
        integrationType,
        apiEndpoint,
        contactName,
        contactEmail,
        connectionPurpose,
        credentials
      };

      const res = await createIntegrationRequest(payload);
      setSuccessMessage(
        `Integration request #${res.id} for '${res.enterpriseName}' has been submitted. Administrators have been notified for review.`
      );
      // Reset form
      setEnterpriseName("");
      setOrganizationName("");
      setApiEndpoint("");
      setConnectionPurpose("");
      setCredentials("");
      await fetchIntegrations();
    } catch (err) {
      setError(err.message || "Failed to submit enterprise integration request");
    } finally {
      setSubmitting(false);
    }
  };

  const handleUpdateStatus = async () => {
    if (!reviewItem) return;
    setReviewing(true);
    setError("");
    setSuccessMessage("");

    try {
      const res = await updateIntegrationStatus(reviewItem.id, {
        status: reviewStatus,
        reason: reviewReason.trim() || undefined
      });
      setSuccessMessage(
        `Integration #${res.id} (${res.enterpriseName}) updated to status: ${res.status}`
      );
      setReviewItem(null);
      setReviewReason("");
      await fetchIntegrations();
    } catch (err) {
      setError(err.message || "Failed to update integration status");
    } finally {
      setReviewing(false);
    }
  };

  const getStatusBadge = (status) => {
    switch (status) {
      case "APPROVED":
      case "ACTIVE":
      case "CONFIGURED":
        return <Badge variant="healthy">{status}</Badge>;
      case "REJECTED":
        return <Badge variant="critical">{status}</Badge>;
      case "PENDING_APPROVAL":
      default:
        return <Badge variant="active">{status}</Badge>;
    }
  };

  return (
    <div style={{ padding: "24px 32px", maxWidth: 1200, margin: "0 auto" }}>
      <div style={{ marginBottom: 24 }}>
        <h1 style={{ fontSize: 24, fontWeight: 700, margin: "0 0 6px 0", color: "var(--text-primary)" }}>
          Enterprise System Integrations
        </h1>
        <p style={{ fontSize: 13, color: "var(--text-secondary)", margin: 0 }}>
          Connect Spectra to external monitoring sources (Datadog, Prometheus, AWS CloudWatch, Kafka) with verified credentials and administrative governance.
        </p>
      </div>

      {error && (
        <div style={{ marginBottom: 16 }}>
          <AlertBanner variant="error" message={error} onClose={() => setError("")} />
        </div>
      )}

      {successMessage && (
        <div style={{ marginBottom: 16 }}>
          <AlertBanner variant="success" message={successMessage} onClose={() => setSuccessMessage("")} />
        </div>
      )}

      <div style={{ display: "grid", gridTemplateColumns: isAdmin ? "1fr" : "1fr 1.2fr", gap: 24, alignItems: "flex-start" }}>
        {/* Request Connection Form (Shown to users or when admin wants to request) */}
        <div style={{
          backgroundColor: "var(--bg-surface)",
          border: "1px solid var(--border-default)",
          borderRadius: "var(--radius-md)",
          padding: 24
        }}>
          <div style={{ fontSize: 16, fontWeight: 700, color: "var(--text-primary)", marginBottom: 4 }}>
            🏢 Connect Enterprise System
          </div>
          <p style={{ fontSize: 12, color: "var(--text-secondary)", marginBottom: 18 }}>
            Submit an authenticated connector configuration. Credentials are encrypted using salted SHA-256 tokens.
          </p>

          <form onSubmit={handleSubmitRequest}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 14 }}>
              <div>
                <label style={{ fontSize: 11, fontWeight: 600, display: "block", marginBottom: 4 }}>
                  Enterprise / System Name *
                </label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. Acme Production Gateway"
                  value={enterpriseName}
                  onChange={(e) => setEnterpriseName(e.target.value)}
                  required
                />
              </div>

              <div>
                <label style={{ fontSize: 11, fontWeight: 600, display: "block", marginBottom: 4 }}>
                  Organization Name *
                </label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. Acme Corp Infrastructure"
                  value={organizationName}
                  onChange={(e) => setOrganizationName(e.target.value)}
                  required
                />
              </div>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 14 }}>
              <div>
                <label style={{ fontSize: 11, fontWeight: 600, display: "block", marginBottom: 4 }}>
                  Integration Source / Type *
                </label>
                <select
                  className="form-input"
                  value={integrationType}
                  onChange={(e) => setIntegrationType(e.target.value)}
                  required
                >
                  <option value="PROMETHEUS">Prometheus Remote Write / Query</option>
                  <option value="DATADOG">Datadog Metrics API</option>
                  <option value="AWS_CLOUDWATCH">AWS CloudWatch Metrics</option>
                  <option value="KAFKA">Apache Kafka Telemetry Topic</option>
                  <option value="OPENTELEMETRY">OpenTelemetry OTLP Collector</option>
                </select>
              </div>

              <div>
                <label style={{ fontSize: 11, fontWeight: 600, display: "block", marginBottom: 4 }}>
                  Target Environment *
                </label>
                <select
                  className="form-input"
                  value={environment}
                  onChange={(e) => setEnvironment(e.target.value)}
                  required
                >
                  <option value="PRODUCTION">PRODUCTION</option>
                  <option value="STAGING">STAGING</option>
                  <option value="CANARY">CANARY</option>
                  <option value="DEV">DEV</option>
                </select>
              </div>
            </div>

            <div style={{ marginBottom: 14 }}>
              <label style={{ fontSize: 11, fontWeight: 600, display: "block", marginBottom: 4 }}>
                API Endpoint URL *
              </label>
              <input
                type="url"
                className="form-input"
                placeholder="https://metrics.internal.acme.com/api/v1"
                value={apiEndpoint}
                onChange={(e) => setApiEndpoint(e.target.value)}
                required
              />
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, marginBottom: 14 }}>
              <div>
                <label style={{ fontSize: 11, fontWeight: 600, display: "block", marginBottom: 4 }}>
                  Technical Contact Name *
                </label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. SRE Lead"
                  value={contactName}
                  onChange={(e) => setContactName(e.target.value)}
                  required
                />
              </div>

              <div>
                <label style={{ fontSize: 11, fontWeight: 600, display: "block", marginBottom: 4 }}>
                  Technical Contact Email *
                </label>
                <input
                  type="email"
                  className="form-input"
                  value={contactEmail}
                  onChange={(e) => setContactEmail(e.target.value)}
                  required
                />
              </div>
            </div>

            <div style={{ marginBottom: 14 }}>
              <label style={{ fontSize: 11, fontWeight: 600, display: "block", marginBottom: 4 }}>
                Connection Purpose / Business Justification
              </label>
              <textarea
                className="form-input"
                rows={2}
                placeholder="Describe metric streams to be ingested (e.g. microservice latencies, error rates)"
                value={connectionPurpose}
                onChange={(e) => setConnectionPurpose(e.target.value)}
              />
            </div>

            <div style={{ marginBottom: 20 }}>
              <label style={{ fontSize: 11, fontWeight: 600, display: "block", marginBottom: 4 }}>
                API Key / Auth Bearer Token (Securely Encrypted)
              </label>
              <input
                type="password"
                className="form-input"
                placeholder="Enter auth token or API secret"
                value={credentials}
                onChange={(e) => setCredentials(e.target.value)}
              />
            </div>

            <Button type="submit" variant="primary" loading={submitting} style={{ width: "100%", justifyContent: "center" }}>
              Submit Enterprise Integration Request 🚀
            </Button>
          </form>
        </div>

        {/* Existing Integrations & Requests List */}
        <div style={{
          backgroundColor: "var(--bg-surface)",
          border: "1px solid var(--border-default)",
          borderRadius: "var(--radius-md)",
          padding: 24
        }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
            <span style={{ fontSize: 16, fontWeight: 700, color: "var(--text-primary)" }}>
              {isAdmin ? "Enterprise Integration Requests & Active Feeds" : "Your Enterprise Connectors"}
            </span>
            <Badge variant="active">{integrations.length} total</Badge>
          </div>

          {loading ? (
            <div style={{ textAlign: "center", padding: "30px 0", color: "var(--text-secondary)", fontSize: 12 }}>
              Loading integration status...
            </div>
          ) : integrations.length === 0 ? (
            <div style={{ textAlign: "center", padding: "36px 0", color: "var(--text-secondary)", fontSize: 12 }}>
              No enterprise integrations registered yet.
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              {integrations.map((item) => (
                <div
                  key={item.id}
                  style={{
                    padding: 14,
                    borderRadius: "var(--radius-sm)",
                    border: "1px solid var(--border-default)",
                    backgroundColor: "var(--bg-subtle)"
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 6 }}>
                    <div>
                      <div style={{ fontSize: 14, fontWeight: 700, color: "var(--text-primary)" }}>
                        {item.enterpriseName}
                      </div>
                      <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>
                        {item.organizationName} • {item.integrationType} ({item.environment})
                      </div>
                    </div>
                    {getStatusBadge(item.status)}
                  </div>

                  <div style={{ fontSize: 11, color: "var(--text-secondary)", marginBottom: 4 }}>
                    Endpoint: <code style={{ fontSize: 10 }}>{item.apiEndpoint}</code>
                  </div>

                  {item.statusReason && (
                    <div style={{ fontSize: 11, color: "var(--text-muted)", fontStyle: "italic", marginTop: 4 }}>
                      Review note: "{item.statusReason}"
                    </div>
                  )}

                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 10, fontSize: 11, color: "var(--text-muted)" }}>
                    <span>Requested by: {item.requestedByEmail}</span>

                    {isAdmin && (
                      <button
                        type="button"
                        onClick={() => setReviewItem(item)}
                        style={{
                          background: "var(--bg-surface)",
                          border: "1px solid var(--border-default)",
                          borderRadius: "var(--radius-sm)",
                          padding: "3px 8px",
                          fontSize: 11,
                          fontWeight: 600,
                          cursor: "pointer",
                          color: "var(--accent-primary)"
                        }}
                      >
                        Manage Request ⚙️
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Admin Review Modal */}
      {reviewItem && (
        <div style={{
          position: "fixed",
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: "rgba(0, 0, 0, 0.65)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          zIndex: 1000,
          padding: 20
        }}>
          <div style={{
            backgroundColor: "var(--bg-surface)",
            border: "1px solid var(--border-default)",
            borderRadius: "var(--radius-md)",
            maxWidth: 500,
            width: "100%",
            padding: 24,
            boxShadow: "var(--shadow-modal)"
          }}>
            <div style={{ fontSize: 16, fontWeight: 700, color: "var(--text-primary)", marginBottom: 12 }}>
              Review Integration Request #{reviewItem.id}
            </div>
            <p style={{ fontSize: 12, color: "var(--text-secondary)", marginBottom: 14 }}>
              Organization: <strong>{reviewItem.organizationName}</strong> | System: <strong>{reviewItem.enterpriseName}</strong>
            </p>

            <div style={{ marginBottom: 14 }}>
              <label style={{ fontSize: 11, fontWeight: 600, display: "block", marginBottom: 4 }}>
                Review Decision *
              </label>
              <select
                className="form-input"
                value={reviewStatus}
                onChange={(e) => setReviewStatus(e.target.value)}
              >
                <option value="APPROVED">APPROVE Integration</option>
                <option value="REJECTED">REJECT Request</option>
                <option value="ACTIVE">Mark as LIVE & ACTIVE</option>
                <option value="PENDING_APPROVAL">Keep PENDING_APPROVAL</option>
              </select>
            </div>

            <div style={{ marginBottom: 20 }}>
              <label style={{ fontSize: 11, fontWeight: 600, display: "block", marginBottom: 4 }}>
                Decision Reason / Notes for Requester
              </label>
              <textarea
                className="form-input"
                rows={3}
                placeholder="e.g. Approved following SRE review. Security clearance verified."
                value={reviewReason}
                onChange={(e) => setReviewReason(e.target.value)}
              />
            </div>

            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <Button
                type="button"
                variant="secondary"
                disabled={reviewing}
                onClick={() => setReviewItem(null)}
              >
                Cancel
              </Button>
              <Button
                type="button"
                variant="primary"
                loading={reviewing}
                onClick={handleUpdateStatus}
              >
                Save Decision & Notify User
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
