import React from "react";

export function AlertBanner({ message, variant = "error", fieldErrors, onClose }) {
  if (!message && !fieldErrors) return null;

  return (
    <div className={`alert-banner alert-banner-${variant}`} role="alert">
      <div style={{ flex: 1 }}>
        {message && <div style={{ fontWeight: 500 }}>{message}</div>}
        {fieldErrors && Object.keys(fieldErrors).length > 0 && (
          <ul style={{ marginTop: 6, paddingLeft: 18, fontSize: 12 }}>
            {Object.entries(fieldErrors).map(([field, error]) => (
              <li key={field}>
                <strong>{field}</strong>: {error}
              </li>
            ))}
          </ul>
        )}
      </div>
      {onClose && (
        <button className="alert-banner-close" onClick={onClose} aria-label="Dismiss alert">
          &times;
        </button>
      )}
    </div>
  );
}
