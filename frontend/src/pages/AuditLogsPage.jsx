import React, { useState, useEffect, useCallback } from "react";
import { getAuditLogs } from "../api/adminApi";
import { Card } from "../components/common/Card";
import { Badge } from "../components/common/Badge";
import { Button } from "../components/common/Button";
import { LoadingSpinner } from "../components/common/LoadingSpinner";
import { AlertBanner } from "../components/common/AlertBanner";
import { EmptyState } from "../components/common/EmptyState";

export function AuditLogsPage() {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const loadLogs = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await getAuditLogs(100);
      setLogs(data || []);
    } catch (err) {
      setError(err.message || "Failed to load administrative audit logs");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadLogs();
  }, [loadLogs]);

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 700, margin: 0 }}>Administrative Audit Logs</h1>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 4 }}>
            Immutable governance log of enterprise onboarding, agent enrollment, credential rotations, and platform operations.
          </p>
        </div>
        <Button variant="secondary" onClick={loadLogs} loading={loading}>↻ Refresh</Button>
      </div>

      {error && <AlertBanner message={error} onClose={() => setError("")} />}

      <Card title={`Recent Audit Events (${logs.length})`}>
        {loading && logs.length === 0 ? (
          <LoadingSpinner label="Loading audit logs..." />
        ) : logs.length === 0 ? (
          <EmptyState title="No Audit Events" description="Administrative actions will be recorded here automatically." />
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Action</th>
                  <th>Performed By</th>
                  <th>Details</th>
                  <th>Timestamp</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((log) => (
                  <tr key={log.id}>
                    <td><Badge variant="neutral">{log.action}</Badge></td>
                    <td style={{ fontWeight: 500 }}>{log.performedBy}</td>
                    <td style={{ fontSize: 12 }}>{log.details}</td>
                    <td style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                      {log.createdAt ? new Date(log.createdAt).toLocaleString() : "-"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
}
