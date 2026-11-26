import React from "react";

export function Card({ title, subtitle, actions, children, className = "", style = {} }) {
  return (
    <div className={`card ${className}`} style={{ minWidth: 0, boxSizing: "border-box", ...style }}>
      {(title || actions) && (
        <div style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "flex-start",
          gap: 12,
          marginBottom: subtitle ? 4 : 12,
          flexWrap: "wrap"
        }}>
          <div style={{ minWidth: 0, flex: "1 1 200px" }}>
            {title && <h3 style={{ fontSize: 15, fontWeight: 600, wordBreak: "break-word" }}>{title}</h3>}
            {subtitle && <p style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 2 }}>{subtitle}</p>}
          </div>
          {actions && <div style={{ flexShrink: 0, alignSelf: "center" }}>{actions}</div>}
        </div>
      )}
      <div style={{ minWidth: 0, width: "100%" }}>
        {children}
      </div>
    </div>
  );
}
