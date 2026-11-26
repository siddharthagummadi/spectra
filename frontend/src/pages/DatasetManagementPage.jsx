import React, { useState, useEffect } from "react";
import { getBatches, deleteBatch } from "../api/ingestionApi";
import { Button } from "../components/common/Button";
import { Badge } from "../components/common/Badge";
import { AlertBanner } from "../components/common/AlertBanner";
import { useAuth } from "../context/AuthContext";
import { Link } from "react-router-dom";

export function DatasetManagementPage() {
  const [batches, setBatches] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [batchToDelete, setBatchToDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);

  const { isAdmin, user } = useAuth();

  const fetchBatches = async () => {
    setLoading(true);
    setError("");
    try {
      const data = await getBatches();
      setBatches(Array.isArray(data) ? data : []);
    } catch (err) {
      setError(err.message || "Failed to load ingestion batches");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchBatches();
    const handleGlobalRefresh = () => fetchBatches();
    window.addEventListener("spectra:refresh", handleGlobalRefresh);
    return () => window.removeEventListener("spectra:refresh", handleGlobalRefresh);
  }, []);

  const handleDeleteConfirm = async () => {
    if (!batchToDelete) return;
    setDeleting(true);
    setError("");
    setSuccessMessage("");

    try {
      const res = await deleteBatch(batchToDelete.id);
      setSuccessMessage(
        `Batch #${batchToDelete.id} (${batchToDelete.filename || "Manual"}) deleted successfully. Records purged: ${res.recordsDeleted}. Historical incidents preserved.`
      );
      setBatchToDelete(null);
      await fetchBatches();
      window.dispatchEvent(new CustomEvent("spectra:refresh"));
    } catch (err) {
      setError(err.message || "Failed to delete ingestion batch");
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div style={{ padding: "24px 32px", maxWidth: 1200, margin: "0 auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 24, flexWrap: "wrap", gap: 16 }}>
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 700, margin: "0 0 6px 0", color: "var(--text-primary)" }}>
            Telemetry Datasets & Ingestion History
          </h1>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", margin: 0 }}>
            Inspect uploaded telemetry batches, track ingestion records, and safely manage dataset lifecycles.
          </p>
        </div>

        <Link
          to="/ingestion"
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 6,
            padding: "8px 16px",
            backgroundColor: "var(--accent-primary)",
            borderRadius: "var(--radius-sm)",
            color: "#ffffff",
            fontSize: 13,
            fontWeight: 600,
            textDecoration: "none"
          }}
        >
          <span>+ Upload New Dataset</span>
        </Link>
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

      {/* Batches Table Card */}
      <div style={{
        backgroundColor: "var(--bg-surface)",
        border: "1px solid var(--border-default)",
        borderRadius: "var(--radius-md)",
        padding: 20
      }}>
        {loading ? (
          <div style={{ textAlign: "center", padding: "40px 0", color: "var(--text-secondary)", fontSize: 13 }}>
            Loading ingestion history...
          </div>
        ) : batches.length === 0 ? (
          <div style={{ textAlign: "center", padding: "48px 0" }}>
            <div style={{ fontSize: 32, marginBottom: 8 }}>📁</div>
            <div style={{ fontSize: 15, fontWeight: 600, color: "var(--text-primary)", marginBottom: 4 }}>
              No Ingestion Batches Found
            </div>
            <p style={{ fontSize: 12, color: "var(--text-secondary)", marginBottom: 16 }}>
              No telemetry datasets have been ingested into the system yet.
            </p>
            <Link
              to="/ingestion"
              style={{
                display: "inline-block",
                padding: "6px 14px",
                backgroundColor: "var(--accent-primary)",
                color: "#fff",
                borderRadius: "var(--radius-sm)",
                fontSize: 12,
                fontWeight: 600,
                textDecoration: "none"
              }}
            >
              Ingest First Dataset
            </Link>
          </div>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
              <thead>
                <tr style={{ borderBottom: "2px solid var(--border-default)", textAlign: "left", color: "var(--text-secondary)" }}>
                  <th style={{ padding: "10px 12px" }}>Batch ID</th>
                  <th style={{ padding: "10px 12px" }}>Source / Filename</th>
                  <th style={{ padding: "10px 12px" }}>Uploaded By</th>
                  <th style={{ padding: "10px 12px" }}>Ingestion Date</th>
                  <th style={{ padding: "10px 12px" }}>Total Records</th>
                  <th style={{ padding: "10px 12px" }}>Ingested</th>
                  <th style={{ padding: "10px 12px" }}>Rejected</th>
                  <th style={{ padding: "10px 12px" }}>Status</th>
                  <th style={{ padding: "10px 12px", textAlign: "right" }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {batches.map((b) => (
                  <tr key={b.id} style={{ borderBottom: "1px solid var(--border-subtle)" }}>
                    <td style={{ padding: "10px 12px", fontFamily: "monospace", fontWeight: 700 }}>
                      #{b.id}
                    </td>
                    <td style={{ padding: "10px 12px", fontWeight: 600, color: "var(--text-primary)" }}>
                      {b.filename || (b.ingestionSource === "MANUAL_ENTRY" ? "Manual Observation" : "Direct Ingestion")}
                    </td>
                    <td style={{ padding: "10px 12px", color: "var(--text-secondary)" }}>
                      {b.uploaderEmail}
                    </td>
                    <td style={{ padding: "10px 12px", color: "var(--text-secondary)" }}>
                      {b.createdAt ? new Date(b.createdAt).toLocaleString() : "-"}
                    </td>
                    <td style={{ padding: "10px 12px", fontWeight: 600 }}>
                      {b.totalRecords}
                    </td>
                    <td style={{ padding: "10px 12px", color: "var(--status-healthy, #10b981)", fontWeight: 700 }}>
                      {b.validRecords}
                    </td>
                    <td style={{ padding: "10px 12px", color: b.rejectedRecords > 0 ? "var(--status-critical, #ef4444)" : "var(--text-secondary)" }}>
                      {b.rejectedRecords}
                    </td>
                    <td style={{ padding: "10px 12px" }}>
                      <Badge variant={b.status === "COMPLETED" ? "healthy" : b.status === "FAILED" ? "critical" : "active"}>
                        {b.status}
                      </Badge>
                    </td>
                    <td style={{ padding: "10px 12px", textAlign: "right" }}>
                      <button
                        type="button"
                        onClick={() => setBatchToDelete(b)}
                        style={{
                          background: "none",
                          border: "1px solid rgba(239, 68, 68, 0.4)",
                          color: "var(--status-critical, #ef4444)",
                          borderRadius: "var(--radius-sm)",
                          padding: "4px 8px",
                          fontSize: 11,
                          fontWeight: 600,
                          cursor: "pointer"
                        }}
                        title="Delete this entire telemetry batch and associated anomaly results"
                      >
                        Delete Batch 🗑️
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Confirmation Modal */}
      {batchToDelete && (
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
            <div style={{ fontSize: 16, fontWeight: 700, color: "var(--status-critical, #ef4444)", marginBottom: 10 }}>
              ⚠️ Confirm Batch Deletion
            </div>
            <p style={{ fontSize: 13, color: "var(--text-primary)", lineHeight: 1.5, marginBottom: 12 }}>
              Are you sure you want to delete Batch <strong>#{batchToDelete.id}</strong> ({batchToDelete.filename || "Manual telemetry"})?
            </p>
            <div style={{
              backgroundColor: "var(--bg-subtle)",
              padding: 12,
              borderRadius: "var(--radius-sm)",
              fontSize: 12,
              color: "var(--text-secondary)",
              lineHeight: 1.6,
              marginBottom: 20
            }}>
              • All <strong>{batchToDelete.validRecords}</strong> persisted telemetry records will be permanently deleted.<br />
              • Associated anomaly evaluation results and non-incident alerts will be purged.<br />
              • Historical incidents are preserved for audit safety.<br />
              • An immutable audit log entry will be recorded.
            </div>

            <div style={{ display: "flex", justifyContent: "flex-end", gap: 10 }}>
              <Button
                type="button"
                variant="secondary"
                disabled={deleting}
                onClick={() => setBatchToDelete(null)}
              >
                Cancel
              </Button>
              <Button
                type="button"
                variant="danger"
                loading={deleting}
                onClick={handleDeleteConfirm}
              >
                Yes, Delete Batch
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
