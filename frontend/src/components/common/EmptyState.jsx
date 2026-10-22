import React from "react";

export function EmptyState({ title = "No records found", description, action }) {
  return (
    <div style={{ textAlign: "center", padding: "40px 20px", color: "var(--text-secondary)" }}>
      <div style={{ width: 44, height: 44, margin: "0 auto 12px", borderRadius: "50%", backgroundColor: "var(--bg-subtle)", display: "flex", alignItems: "center", justifyContent: "center", color: "var(--text-muted)" }}>
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="10" />
          <line x1="8" y1="12" x2="16" y2="12" />
        </svg>
      </div>
      <h4 style={{ fontSize: 14, fontWeight: 600, color: "var(--text-primary)", marginBottom: 4 }}>{title}</h4>
      {description && <p style={{ fontSize: 13, maxWidth: 360, margin: "0 auto 16px" }}>{description}</p>}
      {action && <div>{action}</div>}
    </div>
  );
}
