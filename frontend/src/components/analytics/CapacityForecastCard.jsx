import React, { useState, useEffect } from "react";
import { forecastTelemetry } from "../../api/aiApi";
import { getMetricsByServiceId } from "../../api/metricsApi";
import { Card } from "../common/Card";
import { Badge } from "../common/Badge";
import { LoadingSpinner } from "../common/LoadingSpinner";

export function CapacityForecastCard({ services = [] }) {
  const [selectedServiceId, setSelectedServiceId] = useState("");
  const [selectedMetric, setSelectedMetric] = useState("MEMORY_USAGE");
  const [criticalThreshold, setCriticalThreshold] = useState(90.0);

  const [loading, setLoading] = useState(false);
  const [forecastData, setForecastData] = useState(null);
  const [chartPoints, setChartPoints] = useState([]);
  const [hoveredPoint, setHoveredPoint] = useState(null);
  const [errorMessage, setErrorMessage] = useState("");

  useEffect(() => {
    if (services.length > 0 && !selectedServiceId) {
      setSelectedServiceId(services[0].id);
    }
  }, [services, selectedServiceId]);

  useEffect(() => {
    if (!selectedServiceId) return;

    let isMounted = true;
    setLoading(true);
    setErrorMessage("");

    async function computeForecast() {
      try {
        const rawMetrics = await getMetricsByServiceId(selectedServiceId, 20);
        const filtered = (rawMetrics || [])
          .filter((m) => m.metricName === selectedMetric)
          .sort((a, b) => new Date(a.recordedAt) - new Date(b.recordedAt));

        const values = filtered.map((m) => m.metricValue);

        if (values.length < 3) {
          if (isMounted) {
            setForecastData(null);
            setChartPoints([]);
            setErrorMessage("Insufficient historical telemetry points (minimum 3 required) to compute predictive slope.");
          }
          return;
        }

        const unit = selectedMetric.includes("TIME") ? "MILLISECONDS" : "PERCENT";
        const thresh = selectedMetric.includes("TIME") ? 2500.0 : 90.0;
        setCriticalThreshold(thresh);

        const res = await forecastTelemetry({
          metricName: selectedMetric,
          unit: unit,
          values: values,
          criticalThreshold: thresh
        });

        if (isMounted) {
          setForecastData(res);

          // Build Chart Points: Historical points followed by projected points
          const points = [];
          values.forEach((v, idx) => {
            points.push({
              time: `-${values.length - idx}m`,
              actual: v,
              projected: null,
              isProjected: false
            });
          });

          // Connect the last historical point with projected
          const lastVal = values[values.length - 1];
          if (points.length > 0) {
            points[points.length - 1].projected = lastVal;
          }

          if (res?.projected_values && Array.isArray(res.projected_values)) {
            res.projected_values.forEach((p) => {
              points.push({
                time: `+${p.in_minutes}m`,
                actual: null,
                projected: Math.round(p.projected_value * 10) / 10,
                isProjected: true
              });
            });
          }

          setChartPoints(points);
        }
      } catch (err) {
        if (isMounted) {
          setErrorMessage(err.message || "Failed to generate capacity forecast");
        }
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    computeForecast();

    return () => {
      isMounted = false;
    };
  }, [selectedServiceId, selectedMetric]);

  const riskPct = forecastData?.exhaustion_risk_score_pct != null
    ? Math.round(forecastData.exhaustion_risk_score_pct)
    : 0;

  function getRiskVariant(pct) {
    if (pct >= 70) return "critical";
    if (pct >= 40) return "warning";
    return "healthy";
  }

  function getTrendBadge(direction) {
    switch (direction) {
      case "EXPONENTIAL_SPIKE":
        return <Badge variant="critical">🚨 Spike (+{forecastData.slope_per_minute?.toFixed(1)}/m)</Badge>;
      case "UPWARD_RAPID":
        return <Badge variant="critical">📈 Rapid Growth</Badge>;
      case "UPWARD_MODERATE":
        return <Badge variant="warning">↗ Increasing</Badge>;
      case "DOWNWARD":
        return <Badge variant="healthy">↘ Recovering</Badge>;
      default:
        return <Badge variant="info">➡️ Steady</Badge>;
    }
  }

  // SVG Chart Dimensions & Helpers
  const svgWidth = 600;
  const svgHeight = 200;
  const padLeft = 45;
  const padRight = 30;
  const padTop = 25;
  const padBottom = 30;
  const plotWidth = svgWidth - padLeft - padRight;
  const plotHeight = svgHeight - padTop - padBottom;

  const allVals = chartPoints.flatMap(p => [p.actual, p.projected, criticalThreshold]).filter(v => v != null);
  const minVal = Math.max(0, Math.floor(Math.min(...(allVals.length ? allVals : [0])) / 10) * 10);
  const maxVal = Math.ceil(Math.max(...(allVals.length ? allVals : [100])) / 10) * 10 || 100;
  const valRange = maxVal - minVal || 1;

  const getX = (index) => padLeft + (index / (Math.max(chartPoints.length - 1, 1))) * plotWidth;
  const getY = (val) => padTop + plotHeight - ((val - minVal) / valRange) * plotHeight;

  // Build SVG path strings
  const actualPoints = chartPoints.filter(p => p.actual != null);
  const actualPath = actualPoints.map((p, i) => {
    const origIdx = chartPoints.indexOf(p);
    return `${i === 0 ? "M" : "L"} ${getX(origIdx)} ${getY(p.actual)}`;
  }).join(" ");

  const projectedPoints = chartPoints.filter(p => p.projected != null);
  const projectedPath = projectedPoints.map((p, i) => {
    const origIdx = chartPoints.indexOf(p);
    return `${i === 0 ? "M" : "L"} ${getX(origIdx)} ${getY(p.projected)}`;
  }).join(" ");

  const thresholdY = getY(criticalThreshold);

  return (
    <Card>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
        <div>
          <h3 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>
            🔮 Predictive Capacity & Time-To-Exhaustion (TTE)
          </h3>
          <p style={{ margin: "4px 0 0", fontSize: 12, color: "var(--text-muted)" }}>
            OLS Linear Regression + Autoregressive horizon modeling to prevent threshold breaches.
          </p>
        </div>
      </div>

      {/* Control Filters */}
      <div style={{
        display: "flex",
        gap: 16,
        marginBottom: 16,
        padding: "10px 14px",
        backgroundColor: "var(--bg-subtle)",
        borderRadius: "var(--radius-md)",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap"
      }}>
        <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
          <div>
            <label style={{ fontSize: 11, color: "var(--text-muted)", display: "block", marginBottom: 2 }}>
              Microservice
            </label>
            <select
              value={selectedServiceId}
              onChange={(e) => setSelectedServiceId(e.target.value)}
              style={{
                fontSize: 12,
                padding: "4px 8px",
                borderRadius: "var(--radius-sm)",
                border: "1px solid var(--border-default)",
                backgroundColor: "var(--bg-base)",
                color: "var(--text-primary)"
              }}
            >
              {services.map((s) => (
                <option key={s.id} value={s.id}>{s.name}</option>
              ))}
            </select>
          </div>

          <div>
            <label style={{ fontSize: 11, color: "var(--text-muted)", display: "block", marginBottom: 2 }}>
              Telemetry Signal
            </label>
            <select
              value={selectedMetric}
              onChange={(e) => setSelectedMetric(e.target.value)}
              style={{
                fontSize: 12,
                padding: "4px 8px",
                borderRadius: "var(--radius-sm)",
                border: "1px solid var(--border-default)",
                backgroundColor: "var(--bg-base)",
                color: "var(--text-primary)"
              }}
            >
              <option value="MEMORY_USAGE">MEMORY_USAGE (%)</option>
              <option value="CPU_USAGE">CPU_USAGE (%)</option>
              <option value="RESPONSE_TIME">RESPONSE_TIME (ms)</option>
              <option value="ERROR_RATE">ERROR_RATE (%)</option>
            </select>
          </div>
        </div>

        {forecastData && (
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            {getTrendBadge(forecastData.trend_direction)}
            <Badge variant={getRiskVariant(riskPct)}>
              Risk: {riskPct}%
            </Badge>
          </div>
        )}
      </div>

      {loading && (
        <div style={{ padding: 30, display: "flex", justifyContent: "center" }}>
          <LoadingSpinner />
        </div>
      )}

      {errorMessage && !loading && (
        <div style={{
          padding: "10px 14px",
          backgroundColor: "var(--bg-subtle)",
          color: "var(--text-secondary)",
          borderRadius: "var(--radius-sm)",
          fontSize: 12,
          border: "1px solid var(--border-default)"
        }}>
          ℹ️ {errorMessage}
        </div>
      )}

      {forecastData && !loading && (
        <div>
          {/* Breach Banner */}
          {forecastData.time_to_breach_minutes != null ? (
            <div style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "space-between",
              padding: "10px 14px",
              borderRadius: "var(--radius-md)",
              backgroundColor: "rgba(239, 68, 68, 0.12)",
              border: "1px solid rgba(239, 68, 68, 0.3)",
              marginBottom: 16
            }}>
              <div>
                <strong style={{ fontSize: 13, color: "var(--status-critical)" }}>
                  ⚡ Critical Outage Imminent
                </strong>
                <span style={{ fontSize: 12, color: "var(--text-secondary)", display: "block", marginTop: 2 }}>
                  Metric projected to breach critical threshold ({criticalThreshold}) in approximately{" "}
                  <strong>{forecastData.time_to_breach_minutes.toFixed(1)} minutes</strong>.
                </span>
              </div>
              <div style={{ textAlign: "right" }}>
                <span style={{ fontSize: 10, color: "var(--text-muted)", textTransform: "uppercase" }}>Estimated Slope</span>
                <div style={{ fontSize: 14, fontWeight: 700, color: "var(--status-critical)" }}>
                  {forecastData.slope_per_minute > 0 ? "+" : ""}{forecastData.slope_per_minute?.toFixed(2)} / min
                </div>
              </div>
            </div>
          ) : (
            <div style={{
              padding: "10px 14px",
              borderRadius: "var(--radius-md)",
              backgroundColor: "rgba(16, 185, 129, 0.1)",
              border: "1px solid rgba(16, 185, 129, 0.25)",
              marginBottom: 16,
              fontSize: 12,
              color: "var(--status-healthy)"
            }}>
              🟢 <strong>Stable Capacity:</strong> Current trajectory indicates no threshold breach within the next 30-minute operational horizon.
            </div>
          )}

          {/* Forecasting SVG Chart */}
          <div style={{ position: "relative", width: "100%", height: 220, marginBottom: 14 }}>
            <svg
              viewBox={`0 0 ${svgWidth} ${svgHeight}`}
              style={{ width: "100%", height: "100%", overflow: "visible" }}
            >
              {/* Horizontal Grid lines */}
              {[0, 0.25, 0.5, 0.75, 1.0].map((ratio, i) => {
                const y = padTop + plotHeight * (1 - ratio);
                const val = Math.round(minVal + ratio * valRange);
                return (
                  <g key={i}>
                    <line
                      x1={padLeft}
                      y1={y}
                      x2={svgWidth - padRight}
                      y2={y}
                      stroke="var(--border-subtle, #27272a)"
                      strokeDasharray="3 3"
                    />
                    <text
                      x={padLeft - 8}
                      y={y + 4}
                      fill="var(--text-muted, #71717a)"
                      fontSize="10"
                      textAnchor="end"
                    >
                      {val}
                    </text>
                  </g>
                );
              })}

              {/* Critical Threshold Line */}
              {thresholdY >= padTop && thresholdY <= padTop + plotHeight && (
                <g>
                  <line
                    x1={padLeft}
                    y1={thresholdY}
                    x2={svgWidth - padRight}
                    y2={thresholdY}
                    stroke="#ef4444"
                    strokeWidth="1.5"
                    strokeDasharray="4 4"
                  />
                  <text
                    x={svgWidth - padRight - 4}
                    y={thresholdY - 5}
                    fill="#ef4444"
                    fontSize="10"
                    textAnchor="end"
                    fontWeight="600"
                  >
                    Threshold ({criticalThreshold})
                  </text>
                </g>
              )}

              {/* Historical Telemetry Path */}
              {actualPath && (
                <path
                  d={actualPath}
                  fill="none"
                  stroke="#3b82f6"
                  strokeWidth="2.5"
                  strokeLinecap="round"
                />
              )}

              {/* Projected Trajectory Path */}
              {projectedPath && (
                <path
                  d={projectedPath}
                  fill="none"
                  stroke="#f59e0b"
                  strokeWidth="2.5"
                  strokeDasharray="5 5"
                  strokeLinecap="round"
                />
              )}

              {/* Data points & X-axis labels */}
              {chartPoints.map((p, idx) => {
                const cx = getX(idx);
                const cy = getY(p.actual != null ? p.actual : p.projected);
                const isActual = p.actual != null;
                return (
                  <g key={idx}>
                    {/* X-axis tick label */}
                    {(idx % 2 === 0 || idx === chartPoints.length - 1) && (
                      <text
                        x={cx}
                        y={svgHeight - 8}
                        fill="var(--text-muted, #71717a)"
                        fontSize="10"
                        textAnchor="middle"
                      >
                        {p.time}
                      </text>
                    )}
                    {/* Dot */}
                    <circle
                      cx={cx}
                      cy={cy}
                      r={hoveredPoint === idx ? 6 : 3.5}
                      fill={isActual ? "#3b82f6" : "#f59e0b"}
                      stroke="var(--bg-card, #18181b)"
                      strokeWidth="1.5"
                      style={{ cursor: "pointer", transition: "r 0.15s ease" }}
                      onMouseEnter={() => setHoveredPoint(idx)}
                      onMouseLeave={() => setHoveredPoint(null)}
                    />
                  </g>
                );
              })}
            </svg>

            {/* Hover Tooltip */}
            {hoveredPoint !== null && chartPoints[hoveredPoint] && (
              <div
                style={{
                  position: "absolute",
                  left: `${(getX(hoveredPoint) / svgWidth) * 100}%`,
                  top: `${(getY(chartPoints[hoveredPoint].actual ?? chartPoints[hoveredPoint].projected) / svgHeight) * 100}%`,
                  transform: "translate(-50%, -120%)",
                  backgroundColor: "var(--bg-surface, #1e293b)",
                  border: "1px solid var(--border-default, #475569)",
                  borderRadius: 6,
                  padding: "4px 8px",
                  fontSize: 11,
                  whiteSpace: "nowrap",
                  pointerEvents: "none",
                  boxShadow: "0 4px 6px -1px rgba(0, 0, 0, 0.3)",
                  zIndex: 10
                }}
              >
                <div style={{ color: "var(--text-muted)" }}>Time: {chartPoints[hoveredPoint].time}</div>
                <div style={{ fontWeight: 600, color: chartPoints[hoveredPoint].actual != null ? "#3b82f6" : "#f59e0b" }}>
                  {chartPoints[hoveredPoint].actual != null ? "Actual: " : "Projected: "}
                  {chartPoints[hoveredPoint].actual ?? chartPoints[hoveredPoint].projected}
                </div>
              </div>
            )}
          </div>

          {/* Chart Legend */}
          <div style={{ display: "flex", gap: 20, justifyContent: "center", fontSize: 11, color: "var(--text-muted)", marginBottom: 12 }}>
            <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
              <span style={{ width: 12, height: 3, backgroundColor: "#3b82f6", display: "inline-block", borderRadius: 2 }}></span>
              Historical Observed
            </span>
            <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
              <span style={{ width: 12, height: 0, borderTop: "2px dashed #f59e0b", display: "inline-block" }}></span>
              Projected Trajectory (+30m)
            </span>
            <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
              <span style={{ width: 12, height: 0, borderTop: "2px dashed #ef4444", display: "inline-block" }}></span>
              SLA Critical Threshold
            </span>
          </div>

          {/* Proactive Recommendation */}
          {forecastData.proactive_recommendation && (
            <div style={{
              padding: "10px 14px",
              backgroundColor: "var(--bg-subtle)",
              borderRadius: "var(--radius-sm)",
              border: "1px solid var(--border-default)",
              fontSize: 12,
              lineHeight: 1.5
            }}>
              <strong style={{ color: "var(--text-primary)" }}>🛡️ Proactive SRE Recommendation: </strong>
              <span style={{ color: "var(--text-secondary)" }}>{forecastData.proactive_recommendation}</span>
            </div>
          )}
        </div>
      )}
    </Card>
  );
}
