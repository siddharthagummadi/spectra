import React, { useState, useEffect } from "react";
import {
  previewDataset,
  confirmIngestion,
  downloadTemplateCsv,
  submitManualTelemetry
} from "../api/ingestionApi";
import { getAllServices } from "../api/servicesApi";
import { Button } from "../components/common/Button";
import { Badge } from "../components/common/Badge";
import { AlertBanner } from "../components/common/AlertBanner";
import { useAuth } from "../context/AuthContext";

export function IngestionPage() {
  const [activeTab, setActiveTab] = useState("csv"); // 'csv' or 'manual'
  const { user } = useAuth();

  // CSV State
  const [file, setFile] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [confirmLoading, setConfirmLoading] = useState(false);
  const [previewData, setPreviewData] = useState(null);
  const [ingestionResult, setIngestionResult] = useState(null);
  const [csvError, setCsvError] = useState("");

  // Manual Ingestion State
  const [services, setServices] = useState([]);
  const [manualService, setManualService] = useState("");
  const [manualMetric, setManualMetric] = useState("CPU_USAGE");
  const [manualValue, setManualValue] = useState("");
  const [manualUnit, setManualUnit] = useState("PERCENT");
  const [manualTimestamp, setManualTimestamp] = useState(new Date().toISOString().slice(0, 16));
  const [manualEnv, setManualEnv] = useState("PRODUCTION");
  const [manualScenario, setManualScenario] = useState("");
  const [manualGroundTruth, setManualGroundTruth] = useState("NORMAL");
  const [manualLoading, setManualLoading] = useState(false);
  const [manualMessage, setManualMessage] = useState(null);
  const [manualError, setManualError] = useState("");

  useEffect(() => {
    getAllServices()
      .then((data) => {
        setServices(Array.isArray(data) ? data : []);
        if (data && data.length > 0) {
          setManualService(data[0].name);
        }
      })
      .catch(() => {});
  }, []);

  // Update default unit when metric changes
  useEffect(() => {
    if (manualMetric === "CPU_USAGE" || manualMetric === "ERROR_RATE") {
      setManualUnit("PERCENT");
    } else if (manualMetric === "RESPONSE_TIME") {
      setManualUnit("MILLISECONDS");
    } else if (manualMetric === "MEMORY_USAGE") {
      setManualUnit("PERCENT");
    }
  }, [manualMetric]);

  const handleFileChange = (e) => {
    if (e.target.files && e.target.files[0]) {
      setFile(e.target.files[0]);
      setPreviewData(null);
      setIngestionResult(null);
      setCsvError("");
    }
  };

  const handleGeneratePreview = async () => {
    if (!file) return;
    setPreviewLoading(true);
    setCsvError("");
    setPreviewData(null);
    setIngestionResult(null);

    try {
      const data = await previewDataset(file);
      setPreviewData(data);
    } catch (err) {
      setCsvError(err.message || "Failed to validate dataset format");
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleConfirmIngestion = async () => {
    if (!previewData) return;
    setConfirmLoading(true);
    setCsvError("");

    try {
      const res = await confirmIngestion({
        batchKey: previewData.batchKey,
        filename: previewData.filename,
        records: previewData.previewRows
      });
      setIngestionResult(res);
      setPreviewData(null);
      setFile(null);
      window.dispatchEvent(new CustomEvent("spectra:refresh"));
    } catch (err) {
      setCsvError(err.message || "Failed to finalize dataset ingestion");
    } finally {
      setConfirmLoading(false);
    }
  };

  const handleDownloadTemplate = async () => {
    try {
      const csv = await downloadTemplateCsv();
      const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", "spectra_telemetry_template.csv");
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    } catch (err) {
      setCsvError("Failed to download template: " + err.message);
    }
  };

  const handleManualSubmit = async (e) => {
    e.preventDefault();
    setManualError("");
    setManualMessage(null);
    setManualLoading(true);

    try {
      const payload = {
        serviceName: manualService,
        metricName: manualMetric,
        metricValue: parseFloat(manualValue),
        unit: manualUnit,
        timestamp: manualTimestamp ? new Date(manualTimestamp).toISOString() : new Date().toISOString(),
        environment: manualEnv,
        scenarioLabel: manualScenario.trim() || null,
        groundTruthLabel: manualGroundTruth
      };

      const res = await submitManualTelemetry(payload);
      const alertInfo = res.alertsTriggered ? ` | ${res.alertsTriggered} Alert(s) Triggered` : "";
      const incInfo = res.incidentsCreated ? ` | ${res.incidentsCreated} Incident(s) Created` : "";
      const msg = res.message || `Telemetry observation ingested successfully!${alertInfo}${incInfo}`;
      setManualMessage(msg);
      setManualValue("");
      window.dispatchEvent(new CustomEvent("spectra:refresh"));
    } catch (err) {
      setManualError(err.message || "Failed to persist manual telemetry");
    } finally {
      setManualLoading(false);
    }
  };

  return (
    <div style={{ padding: "24px 32px", maxWidth: 1100, margin: "0 auto" }}>
      <div style={{ marginBottom: 24, display: "flex", justifyContent: "space-between", alignItems: "flex-start", flexWrap: "wrap", gap: 16 }}>
        <div>
          <h1 style={{ fontSize: 24, fontWeight: 700, margin: "0 0 6px 0", color: "var(--text-primary)" }}>
            Telemetry Data Ingestion
          </h1>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", margin: 0 }}>
            Ingest real-world microservice telemetry datasets or submit single manual observations with pipeline validation.
          </p>
        </div>

        <button
          type="button"
          onClick={handleDownloadTemplate}
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 6,
            padding: "8px 14px",
            backgroundColor: "var(--bg-surface)",
            border: "1px solid var(--border-default)",
            borderRadius: "var(--radius-sm)",
            color: "var(--text-primary)",
            fontSize: 12,
            fontWeight: 600,
            cursor: "pointer"
          }}
        >
          <span>📄</span>
          <span>Download CSV Template</span>
        </button>
      </div>

      {/* Ingestion Tabs */}
      <div style={{ display: "flex", borderBottom: "1px solid var(--border-default)", marginBottom: 24 }}>
        <button
          type="button"
          onClick={() => { setActiveTab("csv"); }}
          style={{
            padding: "10px 20px",
            border: "none",
            borderBottom: activeTab === "csv" ? "2px solid var(--accent-primary)" : "2px solid transparent",
            background: "none",
            fontWeight: 600,
            fontSize: 13,
            color: activeTab === "csv" ? "var(--accent-primary)" : "var(--text-secondary)",
            cursor: "pointer",
            display: "flex",
            alignItems: "center",
            gap: 6
          }}
        >
          <span>📁</span>
          <span>CSV Dataset Upload</span>
        </button>
        <button
          type="button"
          onClick={() => { setActiveTab("manual"); }}
          style={{
            padding: "10px 20px",
            border: "none",
            borderBottom: activeTab === "manual" ? "2px solid var(--accent-primary)" : "2px solid transparent",
            background: "none",
            fontWeight: 600,
            fontSize: 13,
            color: activeTab === "manual" ? "var(--accent-primary)" : "var(--text-secondary)",
            cursor: "pointer",
            display: "flex",
            alignItems: "center",
            gap: 6
          }}
        >
          <span>✏️</span>
          <span>Manual Telemetry Entry</span>
        </button>
      </div>

      {activeTab === "csv" ? (
        <div>
          {csvError && (
            <div style={{ marginBottom: 16 }}>
              <AlertBanner variant="error" message={csvError} onClose={() => setCsvError("")} />
            </div>
          )}

          {ingestionResult && (
            <div style={{
              padding: 20,
              backgroundColor: "rgba(16, 185, 129, 0.08)",
              border: "1px solid rgba(16, 185, 129, 0.35)",
              borderRadius: "var(--radius-md)",
              marginBottom: 24
            }}>
              <div style={{ fontSize: 16, fontWeight: 700, color: "var(--status-healthy, #10b981)", marginBottom: 8 }}>
                ✓ Telemetry Batch Successfully Ingested!
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))", gap: 12, marginTop: 12 }}>
                <div style={{ padding: 12, backgroundColor: "var(--bg-surface)", borderRadius: "var(--radius-sm)", border: "1px solid var(--border-subtle)" }}>
                  <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>Batch ID</div>
                  <div style={{ fontSize: 16, fontWeight: 700, color: "var(--text-primary)" }}>#{ingestionResult.batchId}</div>
                </div>
                <div style={{ padding: 12, backgroundColor: "var(--bg-surface)", borderRadius: "var(--radius-sm)", border: "1px solid var(--border-subtle)" }}>
                  <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>Persisted Records</div>
                  <div style={{ fontSize: 16, fontWeight: 700, color: "var(--text-primary)" }}>{ingestionResult.totalIngested ?? ingestionResult.persistedRecords ?? 0}</div>
                </div>
                <div style={{ padding: 12, backgroundColor: "var(--bg-surface)", borderRadius: "var(--radius-sm)", border: "1px solid var(--border-subtle)" }}>
                  <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>Alerts Triggered</div>
                  <div style={{ fontSize: 16, fontWeight: 700, color: "var(--accent-primary)" }}>{ingestionResult.alertsTriggered ?? ingestionResult.alertsGenerated ?? 0}</div>
                </div>
                <div style={{ padding: 12, backgroundColor: "var(--bg-surface)", borderRadius: "var(--radius-sm)", border: "1px solid var(--border-subtle)" }}>
                  <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>Anomalies Detected</div>
                  <div style={{ fontSize: 16, fontWeight: 700, color: "var(--status-critical, #ef4444)" }}>{ingestionResult.anomaliesDetected ?? 0}</div>
                </div>
                <div style={{ padding: 12, backgroundColor: "var(--bg-surface)", borderRadius: "var(--radius-sm)", border: "1px solid var(--border-subtle)" }}>
                  <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>Incidents Created</div>
                  <div style={{ fontSize: 16, fontWeight: 700, color: "#f59e0b" }}>{ingestionResult.incidentsCreated ?? 0}</div>
                </div>
              </div>
              <p style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 12, marginBottom: 0 }}>
                {ingestionResult.message}
              </p>
            </div>
          )}

          {/* Upload Area */}
          <div style={{
            backgroundColor: "var(--bg-surface)",
            border: "1px solid var(--border-default)",
            borderRadius: "var(--radius-md)",
            padding: 24,
            marginBottom: 24
          }}>
            <div style={{ fontSize: 14, fontWeight: 700, color: "var(--text-primary)", marginBottom: 4 }}>
              Select Telemetry CSV File
            </div>
            <p style={{ fontSize: 12, color: "var(--text-secondary)", marginBottom: 16 }}>
              Upload wide-format (one row per timestamp with metric columns) or narrow-format (service_name, metric_name, metric_value, unit, timestamp).
            </p>

            <div style={{
              display: "flex",
              alignItems: "center",
              gap: 12,
              flexWrap: "wrap"
            }}>
              <input
                type="file"
                accept=".csv"
                onChange={handleFileChange}
                style={{
                  fontSize: 13,
                  color: "var(--text-primary)"
                }}
              />

              <Button
                type="button"
                variant="primary"
                loading={previewLoading}
                disabled={!file}
                onClick={handleGeneratePreview}
              >
                Validate & Preview Dataset
              </Button>
            </div>

            {/* Expected Columns Guide */}
            <div style={{ marginTop: 20, padding: 14, backgroundColor: "var(--bg-subtle)", borderRadius: "var(--radius-sm)", fontSize: 11 }}>
              <div style={{ fontWeight: 600, color: "var(--text-primary)", marginBottom: 4 }}>
                Accepted Column Headers:
              </div>
              <div style={{ color: "var(--text-secondary)", lineHeight: 1.6 }}>
                • <strong>Narrow Format:</strong> <code>service_name</code>, <code>metric_name</code>, <code>metric_value</code>, <code>unit</code>, <code>timestamp</code>, <em>(optional: environment, scenario_label, ground_truth_label)</em><br />
                • <strong>Wide Format:</strong> <code>timestamp</code>, <code>service</code>, <code>cpu_usage</code>, <code>memory_usage</code>, <code>response_time</code>, <code>error_rate</code>, <em>(optional: scenario, label)</em>
              </div>
            </div>
          </div>

          {/* Ingestion Preview */}
          {previewData && (
            <div style={{
              backgroundColor: "var(--bg-surface)",
              border: "1px solid var(--border-default)",
              borderRadius: "var(--radius-md)",
              padding: 24,
              marginBottom: 24
            }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16, flexWrap: "wrap", gap: 12 }}>
                <div>
                  <h3 style={{ fontSize: 16, fontWeight: 700, margin: 0, color: "var(--text-primary)" }}>
                    Dataset Preview: {previewData.filename}
                  </h3>
                  <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 4 }}>
                    Format Detected: <strong style={{ color: "var(--accent-primary)" }}>{previewData.format}</strong> | Ground-Truth Labels: {previewData.groundTruthIncluded ? "Yes (Evaluation Ready)" : "No"}
                  </div>
                </div>

                <Button
                  type="button"
                  variant="primary"
                  loading={confirmLoading}
                  disabled={previewData.validRecords === 0 || previewData.duplicateBatch}
                  onClick={handleConfirmIngestion}
                >
                  Confirm & Ingest {previewData.validRecords} Records 🚀
                </Button>
              </div>

              {/* Duplicate Detection Warning */}
              {previewData.duplicateBatch && (
                <div style={{
                  padding: 12,
                  backgroundColor: "rgba(239, 68, 68, 0.1)",
                  border: "1px solid var(--status-critical, #ef4444)",
                  borderRadius: "var(--radius-sm)",
                  color: "var(--status-critical, #ef4444)",
                  fontSize: 12,
                  fontWeight: 600,
                  marginBottom: 16
                }}>
                  ⚠️ {previewData.duplicateWarning || "Duplicate batch detected. This dataset has already been ingested."}
                </div>
              )}

              {/* Record Summary Cards */}
              <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))", gap: 12, marginBottom: 20 }}>
                <div style={{ padding: 12, backgroundColor: "var(--bg-subtle)", borderRadius: "var(--radius-sm)", textAlign: "center" }}>
                  <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>Total Records</div>
                  <div style={{ fontSize: 18, fontWeight: 700, color: "var(--text-primary)" }}>{previewData.totalRecords}</div>
                </div>
                <div style={{ padding: 12, backgroundColor: "rgba(16, 185, 129, 0.1)", borderRadius: "var(--radius-sm)", textAlign: "center" }}>
                  <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>Valid Records</div>
                  <div style={{ fontSize: 18, fontWeight: 700, color: "var(--status-healthy, #10b981)" }}>{previewData.validRecords}</div>
                </div>
                <div style={{ padding: 12, backgroundColor: "rgba(239, 68, 68, 0.08)", borderRadius: "var(--radius-sm)", textAlign: "center" }}>
                  <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>Invalid Records</div>
                  <div style={{ fontSize: 18, fontWeight: 700, color: "var(--status-critical, #ef4444)" }}>{previewData.invalidRecords}</div>
                </div>
                <div style={{ padding: 12, backgroundColor: "var(--bg-subtle)", borderRadius: "var(--radius-sm)", textAlign: "center" }}>
                  <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>Duplicate Records</div>
                  <div style={{ fontSize: 18, fontWeight: 700, color: "var(--text-secondary)" }}>{previewData.duplicateRecords}</div>
                </div>
              </div>

              {/* Sample Rows Table */}
              <div style={{ overflowX: "auto" }}>
                <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
                  <thead>
                    <tr style={{ borderBottom: "2px solid var(--border-default)", textAlign: "left", color: "var(--text-secondary)" }}>
                      <th style={{ padding: "8px 10px" }}>Service</th>
                      <th style={{ padding: "8px 10px" }}>Metric Name</th>
                      <th style={{ padding: "8px 10px" }}>Value</th>
                      <th style={{ padding: "8px 10px" }}>Unit</th>
                      <th style={{ padding: "8px 10px" }}>Timestamp</th>
                      <th style={{ padding: "8px 10px" }}>Scenario</th>
                      <th style={{ padding: "8px 10px" }}>Ground Truth</th>
                    </tr>
                  </thead>
                  <tbody>
                    {previewData.previewRows && previewData.previewRows.slice(0, 30).map((r, idx) => (
                      <tr key={idx} style={{ borderBottom: "1px solid var(--border-subtle)" }}>
                        <td style={{ padding: "8px 10px", fontWeight: 600 }}>{r.serviceName}</td>
                        <td style={{ padding: "8px 10px" }}><code>{r.metricName}</code></td>
                        <td style={{ padding: "8px 10px", fontFamily: "monospace" }}>{r.metricValue}</td>
                        <td style={{ padding: "8px 10px" }}>{r.unit}</td>
                        <td style={{ padding: "8px 10px", color: "var(--text-secondary)" }}>
                          {r.timestamp ? new Date(r.timestamp).toLocaleTimeString() : "-"}
                        </td>
                        <td style={{ padding: "8px 10px" }}>{r.scenarioLabel || "-"}</td>
                        <td style={{ padding: "8px 10px" }}>
                          {r.groundTruthLabel ? (
                            <Badge variant={r.groundTruthLabel.toUpperCase() === "ANOMALY" || r.groundTruthLabel === "1" ? "critical" : "neutral"}>
                              {r.groundTruthLabel}
                            </Badge>
                          ) : "-"}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      ) : (
        /* Manual Ingestion Form */
        <div style={{
          backgroundColor: "var(--bg-surface)",
          border: "1px solid var(--border-default)",
          borderRadius: "var(--radius-md)",
          padding: 28,
          maxWidth: 680
        }}>
          <h2 style={{ fontSize: 16, fontWeight: 700, margin: "0 0 6px 0", color: "var(--text-primary)" }}>
            Manual Telemetry Ingestion
          </h2>
          <p style={{ fontSize: 12, color: "var(--text-secondary)", marginBottom: 20 }}>
            Input and validate a live telemetry measurement against backend anomaly detection and threshold alerting pipelines.
          </p>

          {manualError && (
            <div style={{ marginBottom: 16 }}>
              <AlertBanner variant="error" message={manualError} onClose={() => setManualError("")} />
            </div>
          )}

          {manualMessage && (
            <div style={{ marginBottom: 16 }}>
              <AlertBanner variant="success" message={manualMessage} onClose={() => setManualMessage(null)} />
            </div>
          )}

          <form onSubmit={handleManualSubmit}>
            <div className="grid-2col" style={{ gap: 16, marginBottom: 16 }}>
              <div>
                <label style={{ fontSize: 12, fontWeight: 600, display: "block", marginBottom: 4 }}>
                  Monitored Service *
                </label>
                {services.length > 0 ? (
                  <select
                    className="form-input"
                    value={manualService}
                    onChange={(e) => setManualService(e.target.value)}
                    required
                  >
                    {services.map((s) => (
                      <option key={s.id} value={s.name}>{s.name} ({s.environment})</option>
                    ))}
                  </select>
                ) : (
                  <input
                    type="text"
                    className="form-input"
                    placeholder="e.g. order-service"
                    value={manualService}
                    onChange={(e) => setManualService(e.target.value)}
                    required
                  />
                )}
              </div>

              <div>
                <label style={{ fontSize: 12, fontWeight: 600, display: "block", marginBottom: 4 }}>
                  Metric Type *
                </label>
                <select
                  className="form-input"
                  value={manualMetric}
                  onChange={(e) => setManualMetric(e.target.value)}
                  required
                >
                  <option value="CPU_USAGE">CPU_USAGE (Percentage)</option>
                  <option value="MEMORY_USAGE">MEMORY_USAGE (Percent or MB)</option>
                  <option value="RESPONSE_TIME">RESPONSE_TIME (Milliseconds)</option>
                  <option value="ERROR_RATE">ERROR_RATE (Percentage)</option>
                </select>
              </div>
            </div>

            <div className="grid-2col" style={{ gap: 16, marginBottom: 16 }}>
              <div>
                <label style={{ fontSize: 12, fontWeight: 600, display: "block", marginBottom: 4 }}>
                  Metric Value *
                </label>
                <input
                  type="number"
                  step="any"
                  className="form-input"
                  placeholder="e.g. 84.5"
                  value={manualValue}
                  onChange={(e) => setManualValue(e.target.value)}
                  required
                />
              </div>

              <div>
                <label style={{ fontSize: 12, fontWeight: 600, display: "block", marginBottom: 4 }}>
                  Measurement Unit *
                </label>
                <select
                  className="form-input"
                  value={manualUnit}
                  onChange={(e) => setManualUnit(e.target.value)}
                  required
                >
                  {manualMetric === "RESPONSE_TIME" ? (
                    <option value="MILLISECONDS">MILLISECONDS</option>
                  ) : manualMetric === "MEMORY_USAGE" ? (
                    <>
                      <option value="PERCENT">PERCENT</option>
                      <option value="MEGABYTES">MEGABYTES</option>
                    </>
                  ) : (
                    <option value="PERCENT">PERCENT</option>
                  )}
                </select>
              </div>
            </div>

            <div className="grid-2col" style={{ gap: 16, marginBottom: 16 }}>
              <div>
                <label style={{ fontSize: 12, fontWeight: 600, display: "block", marginBottom: 4 }}>
                  Observation Timestamp *
                </label>
                <input
                  type="datetime-local"
                  className="form-input"
                  value={manualTimestamp}
                  onChange={(e) => setManualTimestamp(e.target.value)}
                  required
                />
              </div>

              <div>
                <label style={{ fontSize: 12, fontWeight: 600, display: "block", marginBottom: 4 }}>
                  Environment *
                </label>
                <select
                  className="form-input"
                  value={manualEnv}
                  onChange={(e) => setManualEnv(e.target.value)}
                  required
                >
                  <option value="PRODUCTION">PRODUCTION</option>
                  <option value="STAGING">STAGING</option>
                  <option value="CANARY">CANARY</option>
                  <option value="DEV">DEV</option>
                </select>
              </div>
            </div>

            <div className="grid-2col" style={{ gap: 16, marginBottom: 24 }}>
              <div>
                <label style={{ fontSize: 12, fontWeight: 600, display: "block", marginBottom: 4 }}>
                  Scenario Label (Optional)
                </label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="e.g. CPU_STARVATION, STEADY_STATE"
                  value={manualScenario}
                  onChange={(e) => setManualScenario(e.target.value)}
                />
              </div>

              <div>
                <label style={{ fontSize: 12, fontWeight: 600, display: "block", marginBottom: 4 }}>
                  Ground-Truth Label
                </label>
                <select
                  className="form-input"
                  value={manualGroundTruth}
                  onChange={(e) => setManualGroundTruth(e.target.value)}
                >
                  <option value="NORMAL">NORMAL (Expected Operating Telemetry)</option>
                  <option value="ANOMALY">ANOMALY (Known Incident / Malfunction)</option>
                </select>
              </div>
            </div>

            <Button type="submit" variant="primary" loading={manualLoading} style={{ width: "100%", justifyContent: "center" }}>
              Submit Observation to Pipeline
            </Button>
          </form>
        </div>
      )}
    </div>
  );
}
