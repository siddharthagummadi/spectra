import React from "react";

export function Badge({ children, variant, className = "" }) {
  const resolveVariant = () => {
    if (variant) return `badge-${variant}`;
    const text = String(children || "").toUpperCase();
    if (["OPEN", "TRIGGERED", "CRITICAL", "HIGH"].includes(text)) {
      return "badge-critical";
    }
    if (["INVESTIGATING", "ACKNOWLEDGED", "MEDIUM", "WARNING"].includes(text)) {
      return "badge-warning";
    }
    if (["RESOLVED", "UP", "ACTIVE", "LOW", "SUCCESS"].includes(text)) {
      return "badge-healthy";
    }
    return "badge-neutral";
  };

  return (
    <span className={`badge ${resolveVariant()} ${className}`}>
      {children}
    </span>
  );
}
