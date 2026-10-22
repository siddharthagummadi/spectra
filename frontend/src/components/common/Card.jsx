import React from "react";

export function Card({ title, subtitle, actions, children, className = "" }) {
  return (
    <div className={`card ${className}`}>
      {(title || actions) && (
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: subtitle ? 4 : 12 }}>
          <div>
            {title && <h3 style={{ fontSize: 15, fontWeight: 600 }}>{title}</h3>}
            {subtitle && <p style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 2 }}>{subtitle}</p>}
          </div>
          {actions && <div>{actions}</div>}
        </div>
      )}
      {children}
    </div>
  );
}
