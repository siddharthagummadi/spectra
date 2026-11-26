import React from "react";
import { NavLink } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

export function Sidebar({ isOpen = false, onClose } = {}) {
  const { user, logout, isAdmin } = useAuth();

  const adminNavItems = [
    { to: "/admin/overview", label: "Platform Overview", icon: "🌐" },
    { to: "/admin/enterprises", label: "Enterprise Connections", icon: "🏢" },
    { to: "/admin/agents", label: "Agent Monitoring", icon: "🛰️" },
    { to: "/analytics", label: "Global Analytics", icon: "🧠" },
    { to: "/admin/audit-logs", label: "Audit Logs", icon: "📜" },
    { to: "/integrations", label: "Integrations Governance", icon: "🔌" }
  ];

  const userNavItems = [
    { to: "/dashboard", label: "Dashboard", icon: "📊" },
    { to: "/services", label: "Services", icon: "⚙️" },
    { to: "/metrics", label: "Metrics & Monitoring", icon: "📉" },
    { to: "/alerts", label: "Anomalies & Alerts", icon: "🔔" },
    { to: "/incidents", label: "Incidents", icon: "🚨" },
    { to: "/agents", label: "Monitoring Agents", icon: "🛰️" },
    { to: "/analytics", label: "AI Analytics", icon: "🧠" },
    { to: "/ingestion", label: "Data Ingestion", icon: "📥" },
    { to: "/datasets", label: "Datasets & Batches", icon: "📁" },
    { to: "/evaluation", label: "Model Evaluation", icon: "📈" },
    { to: "/integrations", label: "Enterprise Connect", icon: "🏢" }
  ];

  const navItems = isAdmin ? adminNavItems : userNavItems;

  return (
    <aside className={`app-sidebar${isOpen ? " is-open" : ""}`} aria-label="Primary navigation">
      <div className="sidebar-header">
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <div style={{ width: 10, height: 10, borderRadius: "50%", backgroundColor: "var(--accent-primary)" }}></div>
            <span style={{ fontSize: 16, fontWeight: 700, letterSpacing: "0.04em", color: "var(--text-primary)" }}>SPECTRA</span>
          </div>
          <div style={{ fontSize: 11, color: "var(--text-secondary)", marginTop: 4, textTransform: "uppercase", letterSpacing: "0.05em" }}>
            {isAdmin ? "Platform Admin Console" : (user?.enterpriseName || "Enterprise Operations")}
          </div>
        </div>
        {onClose && (
          <button
            type="button"
            className="sidebar-close-btn"
            onClick={onClose}
            aria-label="Close navigation menu"
          >
            &times;
          </button>
        )}
      </div>

      <nav style={{ padding: "12px 8px", flex: 1, overflowY: "auto" }}>
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            onClick={() => onClose?.()}
            style={({ isActive }) => ({
              display: "flex",
              alignItems: "center",
              gap: 8,
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
            <span>{item.icon}</span>
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>

      <div style={{ padding: "14px 16px", borderTop: "1px solid var(--border-subtle)", fontSize: 12 }}>
        <div style={{ color: "var(--text-primary)", fontWeight: 500, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }} title={user?.email}>
          {user?.email || "Operator"}
        </div>
        <div style={{ color: "var(--accent-primary)", fontSize: 11, marginTop: 2, fontWeight: 600 }}>
          {isAdmin ? "Platform Administrator" : "Enterprise Operator"}
        </div>
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
