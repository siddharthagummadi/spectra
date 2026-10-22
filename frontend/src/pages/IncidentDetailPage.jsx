import React, { useState, useEffect, useCallback } from "react";
import { useParams, Link } from "react-router-dom";
import { getIncidentById, transitionStatus, getNotes, addNote } from "../api/incidentsApi";
import { useAuth } from "../context/AuthContext";
import { Card } from "../components/common/Card";
import { Badge } from "../components/common/Badge";
import { Button } from "../components/common/Button";
import { Modal } from "../components/common/Modal";
import { AlertBanner } from "../components/common/AlertBanner";
import { LoadingSpinner } from "../components/common/LoadingSpinner";

export function IncidentDetailPage() {
  const { id } = useParams();
  const { user } = useAuth();

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
    } catch (err) {
      setErrorMessage(err.message || "Failed to load incident details");
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadIncidentData();
  }, [loadIncidentData]);

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
      setTransitionLoading(false);
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
      setNoteLoading(false);
    }
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

      <div style={{ display: "grid", gridTemplateColumns: "1.1fr 1fr", gap: 24 }}>
        <div>
          <Card title="Incident Overview" style={{ marginBottom: 20 }}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 14, fontSize: 13 }}>
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

          <Card title="Lifecycle Status Transitions">
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
                  style={{ color: "var(--status-healthy)", borderColor: "var(--status-healthy-border)" }}
                  onClick={() => setResolveModalOpen(true)}
                >
                  Resolve Incident ✓
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
        </div>

        <div>
          <Card title="Investigation Notes & Audit Trail">
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
        title="Resolve Incident"
        onClose={() => setResolveModalOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setResolveModalOpen(false)}>Cancel</Button>
            <Button
              variant="primary"
              loading={transitionLoading}
              onClick={() => handleTransition("RESOLVED", resolutionSummary)}
            >
              Confirm Resolution
            </Button>
          </>
        }
      >
        <div className="form-group">
          <label className="form-label" htmlFor="res-summary">Resolution Summary (Optional)</label>
          <textarea
            id="res-summary"
            className="form-textarea"
            rows="3"
            value={resolutionSummary}
            onChange={(e) => setResolutionSummary(e.target.value)}
            placeholder="Describe root cause mitigation, code fixes, or infrastructure adjustments..."
          />
        </div>
      </Modal>
    </div>
  );
}
