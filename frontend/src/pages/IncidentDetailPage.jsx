import React, { useState, useEffect, useCallback, useRef } from "react";
import { useParams, Link } from "react-router-dom";
import {
  getIncidentById,
  transitionStatus,
  getNotes,
  addNote,
  getIncidentAiDiagnostics,
  triggerIncidentAiDiagnostics
} from "../api/incidentsApi";
import { useAuth } from "../context/AuthContext";
import { Card } from "../components/common/Card";
import { Badge } from "../components/common/Badge";
import { Button } from "../components/common/Button";
import { Modal } from "../components/common/Modal";
import { AlertBanner } from "../components/common/AlertBanner";
import { LoadingSpinner } from "../components/common/LoadingSpinner";

export function IncidentDetailPage() {
  const { id } = useParams();
  const { user, isAdmin } = useAuth();

  const [incident, setIncident] = useState(null);
  const [notes, setNotes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  const [resolveModalOpen, setResolveModalOpen] = useState(false);
  const [resolutionSummary, setResolutionSummary] = useState("");
  const [transitionLoading, setTransitionLoading] = useState(false);

  const [noteText, setNoteText] = useState("");
  const [noteAuthor, setNoteAuthor] = useState(user?.email || "Operator");
  const [noteLoading, setNoteLoading] = useState(false);
  const [noteError, setNoteError] = useState("");

  // Persistent AI Diagnostics state
  const [aiDiagnostics, setAiDiagnostics] = useState(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [aiError, setAiError] = useState("");

  const isMountedRef = useRef(true);
  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  const loadDiagnostics = useCallback(async (incidentId) => {
    try {
      const diagData = await getIncidentAiDiagnostics(incidentId);
      if (isMountedRef.current) {
        setAiDiagnostics(diagData);
        setAiError("");
      }
    } catch (err) {
      if (!isMountedRef.current) return;
      if (err.status === 404) {
        // No existing analysis is a valid operational state
        setAiDiagnostics(null);
        setAiError("");
      } else if (err.status === 401) {
        setAiError("Authentication required to view AI diagnostics.");
      } else if (err.status === 403) {
        setAiError("Access denied: You do not have permission to view AI diagnostics for this incident.");
      } else {
        setAiError(err.message || "Failed to load incident AI diagnostics.");
      }
    }
  }, []);

  const loadIncidentData = useCallback(async () => {
    setLoading(true);
    setErrorMessage("");
    try {
      const [incData, notesData] = await Promise.all([
        getIncidentById(Number(id)),
        getNotes(Number(id))
      ]);
      setIncident(incData);
      setNotes(notesData || []);
      await loadDiagnostics(Number(id));
    } catch (err) {
      setErrorMessage(err.message || "Failed to load incident details");
    } finally {
      if (isMountedRef.current) {
        setLoading(false);
      }
    }
  }, [id, loadDiagnostics]);

  useEffect(() => {
    loadIncidentData();
  }, [loadIncidentData]);

  const handleTriggerDiagnostics = async (forceReanalyze = false) => {
    // Prevent accidental repeated clicks while request is in progress
    if (aiLoading) return;
    setAiLoading(true);
    setAiError("");

    try {
      await triggerIncidentAiDiagnostics(Number(id), forceReanalyze);
      // Fetch latest composite summary (latestExecution & lastSuccessful)
      await loadDiagnostics(Number(id));
    } catch (err) {
      if (!isMountedRef.current) return;
      if (err.status === 401) {
        setAiError("Authentication required to run AI diagnostics.");
      } else if (err.status === 403) {
        setAiError("Access denied: You do not have permission to run AI diagnostics for this incident.");
      } else if (err.status === 404) {
        setAiError("Incident not found.");
      } else {
        setAiError(err.message || "Failed to execute AI diagnostics.");
      }
    } finally {
      if (isMountedRef.current) {
        setAiLoading(false);
      }
    }
  };

  const getActiveSuccessfulDiagnosis = useCallback(() => {
    if (aiDiagnostics?.lastSuccessful) {
      return aiDiagnostics.lastSuccessful;
    }
    if (aiDiagnostics?.latestExecution?.status === "COMPLETED") {
      return aiDiagnostics.latestExecution;
    }
    return null;
  }, [aiDiagnostics]);

  const handleApplyRunbook = (rb) => {
    if (!rb) return;
    const rbId = rb.runbook_id || rb.id || "RB-01";
    const rbTitle = rb.title || "Remediation Procedure";
    const actions = rb.immediate_actions || rb.steps || [];
    const actionText = actions.length > 0
      ? actions.join("; ")
      : "Standard recovery procedure executed.";
    setResolutionSummary(`[AI Runbook ${rbId}: ${rbTitle}] Applied remediation actions: ${actionText}`);
    setResolveModalOpen(true);
  };

  const handleOpenResolutionModal = () => {
    if (!resolutionSummary) {
      const activeSuccess = getActiveSuccessfulDiagnosis();
      const runbooks = activeSuccess?.recommendedRunbooks || [];
      if (runbooks.length > 0) {
        const rb = runbooks[0];
        const rbId = rb.runbook_id || rb.id || "RB-01";
        const rbTitle = rb.title || "Remediation Runbook";
        const actions = rb.immediate_actions || rb.steps || [];
        const actionText = actions.length > 0
          ? actions.join("; ")
          : "Standard operational recovery procedure executed.";
        setResolutionSummary(`[AI Recommended Runbook ${rbId}: ${rbTitle}] Applied mitigation SOP: ${actionText}`);
      } else if (activeSuccess?.primaryRootCause) {
        setResolutionSummary(`Mitigated root cause: ${activeSuccess.primaryRootCause}. Verified metric stability.`);
      }
    }
    setResolveModalOpen(true);
  };

  const handleTransition = async (targetStatus, summary) => {
    setTransitionLoading(true);
    setErrorMessage("");
    setSuccessMessage("");

    try {
      const payload = {
        status: targetStatus,
        author: user?.email || "Operator",
        resolutionSummary: summary || null
      };
      await transitionStatus(Number(id), payload);
      setSuccessMessage(`Incident transitioned to ${targetStatus}`);
      setResolveModalOpen(false);
      setResolutionSummary("");
      loadIncidentData();
    } catch (err) {
      setErrorMessage(err.message || `Failed to transition incident to ${targetStatus}`);
    } finally {
      if (isMountedRef.current) {
        setTransitionLoading(false);
      }
    }
  };

  const handleAddNote = async (e) => {
    e.preventDefault();
    if (!noteText.trim()) return;

    setNoteLoading(true);
    setNoteError("");

    try {
      await addNote(Number(id), {
        author: noteAuthor.trim() || user?.email || "Operator",
        noteText: noteText.trim()
      });
      setNoteText("");
      const updatedNotes = await getNotes(Number(id));
      setNotes(updatedNotes || []);
    } catch (err) {
      setNoteError(err.message || "Failed to add investigation note");
    } finally {
      if (isMountedRef.current) {
        setNoteLoading(false);
      }
    }
  };

  const renderAnalysisDetails = (analysis, isRetainedPrevious = false) => {
    if (!analysis) return null;

    const confidencePercentage =
      analysis.confidenceScore != null
        ? `${(analysis.confidenceScore * 100).toFixed(0)}% Confidence`
        : null;

    const rankedCauses = Array.isArray(analysis.rankedCauses) ? analysis.rankedCauses : [];
    const contributingFactors = Array.isArray(analysis.contributingFactors) ? analysis.contributingFactors : [];
    const runbooks = Array.isArray(analysis.recommendedRunbooks) ? analysis.recommendedRunbooks : [];

    return (
      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        {/* Primary Diagnosis & Confidence */}
        <div
          style={{
            backgroundColor: "var(--bg-subtle)",
            padding: 12,
            borderRadius: "var(--radius-sm)",
            border: isRetainedPrevious
              ? "1px solid var(--status-healthy-border, #a7f3d0)"
              : "1px solid var(--border-subtle)"
          }}
        >
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 6 }}>
            <span
              style={{
                fontSize: 11,
                fontWeight: 700,
                color: isRetainedPrevious ? "var(--status-healthy)" : "var(--accent-primary, #3b82f6)",
                textTransform: "uppercase",
                letterSpacing: "0.5px"
              }}
            >
              {isRetainedPrevious ? "Verified Root Cause (Previous)" : "Probable Root Cause"}
            </span>
            {confidencePercentage && (
              <Badge variant={analysis.confidenceScore >= 0.8 ? "critical" : "warning"}>
                {confidencePercentage}
              </Badge>
            )}
          </div>
          <h3 style={{ fontSize: 15, fontWeight: 700, margin: "4px 0", color: "var(--text-primary)" }}>
            {analysis.primaryRootCause || "Root cause undetermined"}
          </h3>
          {analysis.failureCategory && (
            <div style={{ fontSize: 11, color: "var(--text-muted)", marginTop: 2 }}>
              Category:{" "}
              <code style={{ backgroundColor: "var(--bg-surface)", padding: "1px 5px", borderRadius: 3 }}>
                {analysis.failureCategory}
              </code>
            </div>
          )}
        </div>

        {/* Diagnostic Evidence */}
        <div>
          <span
            style={{
              fontSize: 11,
              fontWeight: 600,
              color: "var(--text-secondary)",
              textTransform: "uppercase",
              display: "block",
              marginBottom: 6
            }}
          >
            Explainable AI (XAI) Diagnostic Evidence
          </span>
          <div
            style={{
              fontSize: 13,
              backgroundColor: "var(--bg-subtle)",
              padding: "10px 12px",
              borderRadius: "var(--radius-sm)",
              borderLeft: isRetainedPrevious
                ? "3px solid var(--status-healthy)"
                : "3px solid var(--accent-primary, #3b82f6)"
            }}
          >
            <p style={{ margin: 0, color: "var(--text-primary)" }}>
              {analysis.primaryEvidence || "Correlated telemetry indicates metric degradation beyond operational baselines."}
            </p>
            {contributingFactors.length > 0 && (
              <ul style={{ margin: "8px 0 0 16px", padding: 0, fontSize: 12, color: "var(--text-secondary)" }}>
                {contributingFactors.map((factor, idx) => (
                  <li key={idx} style={{ marginTop: 2 }}>{factor}</li>
                ))}
              </ul>
            )}
          </div>
        </div>

        {/* Ranked Probable Causes */}
        {rankedCauses.length > 0 && (
          <div>
            <span
              style={{
                fontSize: 11,
                fontWeight: 600,
                color: "var(--text-secondary)",
                textTransform: "uppercase",
                display: "block",
                marginBottom: 6
              }}
            >
              Ranked Probable Causes
            </span>
            <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
              {rankedCauses.map((causeObj, idx) => {
                const causeLabel =
                  typeof causeObj === "string"
                    ? causeObj
                    : causeObj.cause || causeObj.rootCause || JSON.stringify(causeObj);
                const causeConf = causeObj?.confidence != null ? `${(causeObj.confidence * 100).toFixed(0)}%` : null;

                return (
                  <div
                    key={idx}
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      padding: "6px 10px",
                      backgroundColor: "var(--bg-subtle)",
                      borderRadius: "var(--radius-sm)",
                      fontSize: 12
                    }}
                  >
                    <span style={{ color: "var(--text-primary)" }}>{causeLabel}</span>
                    {causeConf && <Badge variant="neutral">{causeConf}</Badge>}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Recommended Runbooks */}
        {runbooks.length > 0 && (
          <div>
            <span
              style={{
                fontSize: 11,
                fontWeight: 600,
                color: "var(--text-secondary)",
                textTransform: "uppercase",
                display: "block",
                marginBottom: 6
              }}
            >
              📚 Recommended Runbooks ({runbooks.length})
            </span>
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {runbooks.map((rb, idx) => {
                const rbId = rb.runbook_id || rb.id || `RB-0${idx + 1}`;
                const rbTitle = rb.title || "Mitigation Runbook";
                const actions = rb.immediate_actions || rb.steps || [];
                const tier = rb.severity_tier || rb.tier || "P1";

                return (
                  <div
                    key={idx}
                    style={{
                      border: "1px solid var(--border-default)",
                      borderRadius: "var(--radius-sm)",
                      padding: 12,
                      backgroundColor: "var(--bg-subtle)"
                    }}
                  >
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 6 }}>
                      <strong style={{ fontSize: 13, color: "var(--text-primary)" }}>
                        {rbTitle} ({rbId})
                      </strong>
                      <Badge variant="healthy">Tier: {tier}</Badge>
                    </div>

                    {actions.length > 0 && (
                      <div style={{ marginTop: 8 }}>
                        <span style={{ fontSize: 11, color: "var(--text-muted)", display: "block", marginBottom: 4 }}>
                          Standard Operating Procedure:
                        </span>
                        <ol style={{ margin: 0, paddingLeft: 18, fontSize: 12, color: "var(--text-primary)" }}>
                          {actions.map((act, i) => (
                            <li key={i} style={{ marginBottom: 3 }}>{act}</li>
                          ))}
                        </ol>
                      </div>
                    )}

                    {!isAdmin && incident.status !== "RESOLVED" && incident.status !== "CLOSED" && (
                      <Button
                        variant="secondary"
                        onClick={() => handleApplyRunbook(rb)}
                        style={{ marginTop: 10, fontSize: 12, width: "100%", justifyContent: "center" }}
                      >
                        ⚡ Apply Runbook Action to Resolution
                      </Button>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {/* Execution Metadata */}
        <div
          style={{
            fontSize: 11,
            color: "var(--text-muted)",
            display: "flex",
            justifyContent: "space-between",
            flexWrap: "wrap",
            gap: 8,
            paddingTop: 8,
            borderTop: "1px solid var(--border-subtle)"
          }}
        >
          <span>Version: #{analysis.version || 1}</span>
          {analysis.executionId && <span>Exec ID: <code>{analysis.executionId.slice(0, 8)}...</code></span>}
          {analysis.completedAt && (
            <span>Completed: {new Date(analysis.completedAt).toLocaleString()}</span>
          )}
        </div>
      </div>
    );
  };

  if (loading && !incident) {
    return <LoadingSpinner message="Loading incident..." />;
  }

  if (!incident) {
    return (
      <div>
        <AlertBanner variant="error" message={errorMessage || "Incident not found"} />
        <Link to="/incidents" style={{ color: "var(--accent-primary)" }}>← Back to Incidents</Link>
      </div>
    );
  }

  const latestExecution = aiDiagnostics?.latestExecution;
  const lastSuccessful = aiDiagnostics?.lastSuccessful;

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Link to="/incidents" style={{ color: "var(--text-secondary)", fontSize: 13, textDecoration: "none" }}>
          ← Back to Incidents
        </Link>
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20 }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <span style={{ fontSize: 18, color: "var(--text-secondary)", fontWeight: 600 }}>#{incident.id}</span>
            <h1 style={{ fontSize: 22, fontWeight: 700 }}>{incident.title}</h1>
            <Badge>{incident.status}</Badge>
            <Badge>{incident.severity}</Badge>
          </div>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 4 }}>
            Service: <strong>{incident.serviceName}</strong> · Triggered: {new Date(incident.triggeredAt).toLocaleString()}
          </p>
        </div>
      </div>

      {errorMessage && (
        <AlertBanner variant="error" message={errorMessage} onClose={() => setErrorMessage("")} />
      )}

      {successMessage && (
        <AlertBanner variant="success" message={successMessage} onClose={() => setSuccessMessage("")} />
      )}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 460px), 1fr))", gap: 24, alignItems: "start" }}>
        <div style={{ minWidth: 0 }}>
          {/* Explainable AI (XAI) Incident Diagnostics Card */}
          <Card
            title={
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", width: "100%", flexWrap: "wrap", gap: 8 }}>
                <span style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <span style={{ fontSize: 16 }}>⚡</span> Explainable AI (XAI) — Incident Diagnostics
                </span>
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  {latestExecution && (
                    <Badge
                      variant={
                        latestExecution.status === "COMPLETED"
                          ? "healthy"
                          : latestExecution.status === "FAILED"
                          ? "critical"
                          : "warning"
                      }
                    >
                      {latestExecution.status} (V{latestExecution.version})
                    </Badge>
                  )}
                  {latestExecution && (
                    <Button
                      variant="secondary"
                      loading={aiLoading}
                      disabled={aiLoading}
                      onClick={() => handleTriggerDiagnostics(true)}
                      style={{ fontSize: 11, padding: "2px 8px" }}
                    >
                      {aiLoading ? "Analyzing..." : "Re-diagnose ⚡"}
                    </Button>
                  )}
                </div>
              </div>
            }
            style={{ marginBottom: 20, border: "1px solid var(--accent-subtle, #3b82f633)", backgroundColor: "var(--bg-surface)" }}
          >
            {aiError && (
              <div style={{ marginBottom: 12 }}>
                <AlertBanner variant="error" message={aiError} onClose={() => setAiError("")} />
              </div>
            )}

            {aiLoading && !latestExecution ? (
              <div style={{ padding: "20px 0", textAlign: "center" }}>
                <LoadingSpinner message="Orchestrating multi-signal incident AI diagnostics..." />
              </div>
            ) : !latestExecution ? (
              /* State 1: No existing analysis */
              <div style={{ textAlign: "center", padding: "16px 8px", color: "var(--text-muted)", fontSize: 13 }}>
                <p style={{ margin: "0 0 6px 0" }}>No AI diagnostics have been run for this incident yet.</p>
                <p style={{ margin: "0 0 14px 0", fontSize: 12, color: "var(--text-secondary)" }}>
                  Evaluate multi-signal telemetry metrics, alert events, and anomalies to identify the probable root cause.
                </p>
                <Button
                  variant="primary"
                  loading={aiLoading}
                  disabled={aiLoading}
                  onClick={() => handleTriggerDiagnostics(false)}
                >
                  {aiLoading ? "Executing AI Diagnosis..." : "Execute AI Diagnosis ⚡"}
                </Button>
              </div>
            ) : latestExecution.status === "PROCESSING" || latestExecution.status === "PENDING" ? (
              /* State 2: PROCESSING state */
              <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: 12,
                    padding: 12,
                    backgroundColor: "var(--bg-subtle)",
                    border: "1px solid var(--status-warning-border, #f59e0b)",
                    borderRadius: "var(--radius-sm)"
                  }}
                >
                  <LoadingSpinner size="sm" />
                  <div>
                    <strong style={{ fontSize: 13, color: "var(--text-primary)" }}>
                      AI Diagnostics in Progress (Version #{latestExecution.version})
                    </strong>
                    <p style={{ margin: "2px 0 0 0", fontSize: 12, color: "var(--text-secondary)" }}>
                      Evaluating telemetry metrics, alert events, and remediation runbooks...
                    </p>
                  </div>
                </div>

                {/* If previous success exists, show it while re-analysis is in flight */}
                {lastSuccessful && (
                  <div style={{ borderTop: "1px solid var(--border-subtle)", paddingTop: 14 }}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                      <span style={{ fontSize: 11, fontWeight: 700, color: "var(--text-secondary)", textTransform: "uppercase" }}>
                        Previous Verified Diagnosis (Version #{lastSuccessful.version})
                      </span>
                      <Badge variant="healthy">V{lastSuccessful.version}</Badge>
                    </div>
                    {renderAnalysisDetails(lastSuccessful, true)}
                  </div>
                )}
              </div>
            ) : latestExecution.status === "FAILED" ? (
              /* State 3: FAILED state */
              <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
                <div
                  style={{
                    padding: 14,
                    backgroundColor: "var(--bg-subtle)",
                    border: "1px solid var(--status-critical-border, #ef4444)",
                    borderRadius: "var(--radius-sm)"
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 6 }}>
                    <strong style={{ fontSize: 13, color: "var(--status-critical, #ef4444)" }}>
                      Latest AI Analysis Failed (Version #{latestExecution.version})
                    </strong>
                    <Badge variant="critical">FAILED</Badge>
                  </div>
                  <p style={{ margin: 0, fontSize: 12, color: "var(--text-primary)" }}>
                    {latestExecution.failureReason || "AI microservice was unable to complete the analysis."}
                  </p>
                  {latestExecution.executionId && (
                    <div style={{ fontSize: 11, color: "var(--text-muted)", marginTop: 6 }}>
                      Execution ID: <code>{latestExecution.executionId}</code>
                    </div>
                  )}
                  <div style={{ marginTop: 10 }}>
                    <Button
                      variant="secondary"
                      loading={aiLoading}
                      disabled={aiLoading}
                      onClick={() => handleTriggerDiagnostics(true)}
                      style={{ fontSize: 11, padding: "2px 8px" }}
                    >
                      Retry Diagnostics ↻
                    </Button>
                  </div>
                </div>

                {/* Critical Requirement: Failed reanalysis must NOT hide previous successful diagnosis */}
                {lastSuccessful && (
                  <div
                    style={{
                      marginTop: 4,
                      paddingTop: 14,
                      borderTop: "2px dashed var(--border-default)"
                    }}
                  >
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
                      <div>
                        <span style={{ fontSize: 12, fontWeight: 700, color: "var(--status-healthy)", textTransform: "uppercase" }}>
                          ✓ Retained Previous Successful Diagnosis (Version #{lastSuccessful.version})
                        </span>
                        <p style={{ fontSize: 12, color: "var(--text-secondary)", margin: "2px 0 0 0" }}>
                          The latest re-analysis attempt failed. Displaying previously verified operational diagnosis.
                        </p>
                      </div>
                      <Badge variant="healthy">STABLE V{lastSuccessful.version}</Badge>
                    </div>
                    {renderAnalysisDetails(lastSuccessful, true)}
                  </div>
                )}
              </div>
            ) : (
              /* State 4: COMPLETED state */
              <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
                {renderAnalysisDetails(latestExecution, false)}
              </div>
            )}
          </Card>

          {!isAdmin && (
            <Card title="Lifecycle Status Transitions" style={{ marginBottom: 20 }}>
              <p style={{ fontSize: 13, color: "var(--text-secondary)", marginBottom: 14 }}>
                Execute permitted lifecycle transitions according to operational protocol.
              </p>

              <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
                {incident.status === "OPEN" && (
                  <Button
                    variant="primary"
                    loading={transitionLoading}
                    onClick={() => handleTransition("INVESTIGATING")}
                  >
                    Start Investigation →
                  </Button>
                )}

                {(incident.status === "OPEN" || incident.status === "INVESTIGATING") && (
                  <Button
                    variant="secondary"
                    style={{ color: "var(--status-healthy)", borderColor: "var(--status-healthy-border)", fontWeight: 600 }}
                    onClick={handleOpenResolutionModal}
                  >
                    💡 Review & Apply AI Resolution Plan ✓
                  </Button>
                )}

                {incident.status === "RESOLVED" && (
                  <>
                    <Button
                      variant="primary"
                      loading={transitionLoading}
                      onClick={() => handleTransition("CLOSED")}
                    >
                      Close Incident
                    </Button>
                    <Button
                      variant="secondary"
                      loading={transitionLoading}
                      onClick={() => handleTransition("INVESTIGATING")}
                    >
                      Reopen to Investigating ↺
                    </Button>
                  </>
                )}

                {incident.status === "CLOSED" && (
                  <div style={{ fontSize: 13, color: "var(--text-muted)" }}>
                    This incident is closed (terminal state). No further status transitions allowed.
                  </div>
                )}
              </div>
            </Card>
          )}

          <Card title="Incident Overview" style={{ marginBottom: 20 }}>
            <div className="grid-2col" style={{ fontSize: 13 }}>
              <div>
                <span style={{ color: "var(--text-secondary)", display: "block", fontSize: 11, textTransform: "uppercase" }}>Current Status</span>
                <strong style={{ fontSize: 14 }}>{incident.status}</strong>
              </div>
              <div>
                <span style={{ color: "var(--text-secondary)", display: "block", fontSize: 11, textTransform: "uppercase" }}>Severity Level</span>
                <strong style={{ fontSize: 14 }}>{incident.severity}</strong>
              </div>
              <div>
                <span style={{ color: "var(--text-secondary)", display: "block", fontSize: 11, textTransform: "uppercase" }}>Assigned SRE</span>
                <span>{incident.assignedTo || "Unassigned"}</span>
              </div>
              <div>
                <span style={{ color: "var(--text-secondary)", display: "block", fontSize: 11, textTransform: "uppercase" }}>Linked Alert</span>
                <span>{incident.alertId ? `#${incident.alertId}` : "Manual (None)"}</span>
              </div>
              <div>
                <span style={{ color: "var(--text-secondary)", display: "block", fontSize: 11, textTransform: "uppercase" }}>Triggered At (UTC)</span>
                <span>{new Date(incident.triggeredAt).toUTCString()}</span>
              </div>
              <div>
                <span style={{ color: "var(--text-secondary)", display: "block", fontSize: 11, textTransform: "uppercase" }}>Resolved At</span>
                <span>{incident.resolvedAt ? new Date(incident.resolvedAt).toLocaleString() : "Unresolved"}</span>
              </div>
            </div>

            {incident.description && (
              <div style={{ marginTop: 16, paddingTop: 14, borderTop: "1px solid var(--border-subtle)" }}>
                <span style={{ color: "var(--text-secondary)", fontSize: 11, textTransform: "uppercase", display: "block", marginBottom: 4 }}>
                  Description / Impact
                </span>
                <p style={{ fontSize: 13, color: "var(--text-primary)", whiteSpace: "pre-wrap" }}>
                  {incident.description}
                </p>
              </div>
            )}

            {incident.resolutionSummary && (
              <div style={{ marginTop: 16, paddingTop: 14, borderTop: "1px solid var(--border-subtle)" }}>
                <span style={{ color: "var(--status-healthy)", fontSize: 11, textTransform: "uppercase", display: "block", marginBottom: 4, fontWeight: 600 }}>
                  Resolution Summary
                </span>
                <p style={{ fontSize: 13, color: "var(--text-primary)", backgroundColor: "var(--status-healthy-bg)", padding: 10, borderRadius: "var(--radius-sm)" }}>
                  {incident.resolutionSummary}
                </p>
              </div>
            )}
          </Card>
        </div>

        <div style={{ minWidth: 0 }}>
          <Card title="Investigation Notes & Audit Trail">
            {!isAdmin && (
              <form onSubmit={handleAddNote} style={{ marginBottom: 20 }}>
                {noteError && (
                  <AlertBanner variant="error" message={noteError} onClose={() => setNoteError("")} />
                )}
                <div className="form-group">
                  <label className="form-label" htmlFor="note-author">Author</label>
                  <input
                    id="note-author"
                    type="text"
                    className="form-input"
                    value={noteAuthor}
                    onChange={(e) => setNoteAuthor(e.target.value)}
                    required
                  />
                </div>
                <div className="form-group">
                  <label className="form-label" htmlFor="note-text">Investigation Note</label>
                  <textarea
                    id="note-text"
                    className="form-textarea"
                    rows="3"
                    value={noteText}
                    onChange={(e) => setNoteText(e.target.value)}
                    required
                    placeholder="Record investigative findings, mitigation actions, or diagnostic output..."
                  />
                </div>
                <Button type="submit" variant="primary" loading={noteLoading}>
                  Add Note
                </Button>
              </form>
            )}

            <div style={{ borderTop: "1px solid var(--border-subtle)", paddingTop: 16 }}>
              <h4 style={{ fontSize: 13, fontWeight: 600, color: "var(--text-secondary)", marginBottom: 12 }}>
                Chronological History ({notes.length})
              </h4>

              {notes.length === 0 ? (
                <div style={{ color: "var(--text-muted)", fontSize: 13, textAlign: "center", padding: 16 }}>
                  No investigation notes recorded yet.
                </div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                  {notes.map((n) => (
                    <div
                      key={n.id}
                      style={{
                        padding: 12,
                        backgroundColor: "var(--bg-subtle)",
                        border: "1px solid var(--border-subtle)",
                        borderRadius: "var(--radius-sm)",
                        fontSize: 13
                      }}
                    >
                      <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 4 }}>
                        <span style={{ fontWeight: 600, color: "var(--text-primary)" }}>{n.author}</span>
                        <span style={{ fontSize: 11, color: "var(--text-muted)" }}>
                          {new Date(n.createdAt).toLocaleTimeString()} · {new Date(n.createdAt).toLocaleDateString()}
                        </span>
                      </div>
                      <p style={{ color: "var(--text-primary)", whiteSpace: "pre-wrap" }}>
                        {n.noteText}
                      </p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </Card>
        </div>
      </div>

      <Modal
        isOpen={resolveModalOpen}
        title="💡 AI Recommended Resolution Plan & Execution"
        onClose={() => setResolveModalOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setResolveModalOpen(false)}>Cancel</Button>
            <Button
              variant="primary"
              loading={transitionLoading}
              onClick={() => handleTransition("RESOLVED", resolutionSummary)}
            >
              Confirm & Apply Resolution ✓
            </Button>
          </>
        }
      >
        <div style={{ marginBottom: 14 }}>
          <span style={{ fontSize: 11, fontWeight: 700, color: "var(--accent-primary, #3b82f6)", textTransform: "uppercase", display: "block" }}>
            Identified Root Cause:
          </span>
          <div style={{ fontSize: 13, fontWeight: 600, color: "var(--text-primary)", marginTop: 2 }}>
            {getActiveSuccessfulDiagnosis()?.primaryRootCause || incident.title}
          </div>
        </div>

        {(() => {
          const runbooks = getActiveSuccessfulDiagnosis()?.recommendedRunbooks || [];
          if (runbooks.length === 0) return null;
          const firstRb = runbooks[0];
          const actions = firstRb.immediate_actions || firstRb.steps || [];
          if (actions.length === 0) return null;

          return (
            <div style={{
              marginBottom: 16,
              padding: 12,
              backgroundColor: "var(--bg-subtle)",
              border: "1px solid var(--border-subtle)",
              borderRadius: "var(--radius-sm)"
            }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 6 }}>
                <span style={{ fontSize: 11, fontWeight: 700, color: "var(--text-secondary)", textTransform: "uppercase" }}>
                  📖 Recommended SOP Mitigation Steps:
                </span>
                <button
                  type="button"
                  onClick={() => handleApplyRunbook(firstRb)}
                  style={{
                    background: "none",
                    border: "none",
                    color: "var(--accent-primary)",
                    fontSize: 11,
                    fontWeight: 600,
                    cursor: "pointer",
                    padding: 0
                  }}
                >
                  Insert into summary ⚡
                </button>
              </div>
              <ol style={{ margin: 0, paddingLeft: 18, fontSize: 12, color: "var(--text-primary)" }}>
                {actions.map((act, i) => (
                  <li key={i} style={{ marginBottom: 4 }}>{act}</li>
                ))}
              </ol>
            </div>
          );
        })()}

        <div className="form-group">
          <label className="form-label" htmlFor="res-summary">
            Applied Resolution & Post-Mortem Actions
          </label>
          <textarea
            id="res-summary"
            className="form-textarea"
            rows="4"
            value={resolutionSummary}
            onChange={(e) => setResolutionSummary(e.target.value)}
            placeholder="Record the executed mitigation actions (e.g. Scaled connection pool, evicted zombie sessions, verified response time)..."
          />
        </div>

        <div style={{ fontSize: 11, color: "var(--text-muted)", marginTop: 8 }}>
          ℹ️ <strong>AIOps Human-in-the-Loop Standard:</strong> AI formulates the diagnostic resolution plan; the on-call engineer verifies and records execution before resolving.
        </div>
      </Modal>
    </div>
  );
}
