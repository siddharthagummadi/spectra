import React from "react";

export function Button({
  children,
  variant = "primary",
  disabled = false,
  loading = false,
  type = "button",
  onClick,
  className = "",
  style
}) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      onClick={onClick}
      style={style}
      className={`btn btn-${variant} ${className}`}
    >
      {loading ? "Please wait..." : children}
    </button>
  );
}
