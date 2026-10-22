import React from "react";

export function LoadingSpinner({ message = "Loading data...", size = 20 }) {
  return (
    <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 10, padding: "24px 16px", color: "var(--text-secondary)" }}>
      <svg
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        style={{ animation: "spin 1s linear infinite" }}
      >
        <style>{`@keyframes spin { 100% { transform: rotate(360deg); } }`}</style>
        <circle cx="12" cy="12" r="10" strokeOpacity="0.25" />
        <path d="M12 2a10 10 0 0 1 10 10" />
      </svg>
      {message && <span style={{ fontSize: 13 }}>{message}</span>}
    </div>
  );
}
