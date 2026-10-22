import React from "react";
import { NavLink } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

export function Sidebar() {
  const { user, logout } = useAuth();

  const navItems = [
    { to: "/dashboard", label: "Dashboard" },
    { to: "/services", label: "Services" },
    { to: "/metrics", label: "Metrics" },
    { to: "/alerts", label: "Alerts" },
    { to: "/incidents", label: "Incidents" }
  ];

  return (
    <aside style={{
      width: 220,
      backgroundColor: "var(--bg-surface)",
      borderRight: "1px solid var(--border-default)",
      display: "flex",
      flexDirection: "column",
      minHeight: "100vh"
    }}>
      <div style={{ padding: "18px 20px", borderBottom: "1px solid var(--border-subtle)" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <div style={{ width: 10, height: 10, borderRadius: "50%", backgroundColor: "var(--accent-primary)" }}></div>
          <span style={{ fontSize: 16, fontWeight: 700, letterSpacing: "0.04em", color: "var(--text-primary)" }}>SPECTRA</span>
        </div>
        <div style={{ fontSize: 11, color: "var(--text-secondary)", marginTop: 4, textTransform: "uppercase", letterSpacing: "0.05em" }}>
          Operations Platform
        </div>
      </div>

      <nav style={{ padding: "12px 8px", flex: 1 }}>
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            style={({ isActive }) => ({
              display: "flex",
              alignItems: "center",
              padding: "8px 12px",
              borderRadius: "var(--radius-sm)",
              fontSize: 13,
              fontWeight: 500,
              color: isActive ? "var(--accent-primary)" : "var(--text-secondary)",
              backgroundColor: isActive ? "var(--accent-light)" : "transparent",
              marginBottom: 2,
              textDecoration: "none"
            })}
          >
            {item.label}
          </NavLink>
        ))}
      </nav>

      <div style={{ padding: "14px 16px", borderTop: "1px solid var(--border-subtle)", fontSize: 12 }}>
        <div style={{ color: "var(--text-primary)", fontWeight: 500, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }} title={user?.email}>
          {user?.email || "Operator"}
        </div>
        <div style={{ color: "var(--text-muted)", fontSize: 11, marginTop: 2 }}>In-Memory Session</div>
        <button
          onClick={logout}
          style={{
            marginTop: 10,
            background: "none",
            border: "none",
            color: "var(--status-critical)",
            fontSize: 12,
            cursor: "pointer",
            padding: 0,
            textAlign: "left"
          }}
        >
          Sign Out
        </button>
      </div>
    </aside>
  );
}
