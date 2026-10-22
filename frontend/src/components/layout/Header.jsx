import React, { useState, useEffect } from "react";
import { checkBackendHealth } from "../../api/usersApi";
import { Badge } from "../common/Badge";
import { useAuth } from "../../context/AuthContext";

export function Header({ title }) {
  const [healthStatus, setHealthStatus] = useState("CHECKING");
  const [refreshing, setRefreshing] = useState(false);
  const { user, logout } = useAuth();

  const fetchHealth = () => {
    return checkBackendHealth()
      .then((res) => {
        setHealthStatus(res?.status === "UP" ? "UP" : "DEGRADED");
      })
      .catch(() => {
        setHealthStatus("UNAVAILABLE");
      });
  };

  useEffect(() => {
    let isMounted = true;
    checkBackendHealth()
      .then((res) => {
        if (isMounted) {
          setHealthStatus(res?.status === "UP" ? "UP" : "DEGRADED");
        }
      })
      .catch(() => {
        if (isMounted) {
          setHealthStatus("UNAVAILABLE");
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  const handleRefresh = async () => {
    if (refreshing) return;
    setRefreshing(true);
    try {
      window.dispatchEvent(new CustomEvent("spectra:refresh"));
      await fetchHealth();
    } finally {
      setTimeout(() => {
        setRefreshing(false);
      }, 400);
    }
  };

  return (
    <header style={{
      height: 52,
      backgroundColor: "var(--bg-surface)",
      borderBottom: "1px solid var(--border-default)",
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      padding: "0 24px"
    }}>
      <div style={{ fontSize: 15, fontWeight: 600, color: "var(--text-primary)" }}>
        {title || "Spectra Operations Platform"}
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: 16 }}>
        <button
          type="button"
          onClick={handleRefresh}
          disabled={refreshing}
          style={{
            background: "none",
            border: "1px solid var(--border-default)",
            color: "var(--text-secondary)",
            fontSize: 12,
            cursor: refreshing ? "not-allowed" : "pointer",
            padding: "4px 10px",
            borderRadius: "var(--radius-sm)"
          }}
          onMouseOver={(e) => (e.target.style.color = "var(--text-primary)")}
          onMouseOut={(e) => (e.target.style.color = "var(--text-secondary)")}
        >
          {refreshing ? "Refreshing..." : "Refresh"}
        </button>

        <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12 }}>
          <span style={{ color: "var(--text-secondary)" }}>Backend:</span>
          <Badge variant={healthStatus === "UP" ? "healthy" : "critical"}>
            {healthStatus}
          </Badge>
        </div>

        <div style={{ width: 1, height: 18, backgroundColor: "var(--border-default)" }}></div>

        <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 13, color: "var(--text-secondary)" }}>
          <span>{user?.email}</span>
          <Badge variant={user?.role === "ADMIN" ? "active" : "neutral"}>
            {user?.role || "USER"}
          </Badge>
        </div>

        <button
          type="button"
          onClick={logout}
          style={{
            background: "none",
            border: "none",
            color: "var(--text-secondary)",
            fontSize: 13,
            cursor: "pointer",
            padding: "4px 8px",
            borderRadius: "var(--radius-sm)"
          }}
          onMouseOver={(e) => (e.target.style.color = "var(--text-primary)")}
          onMouseOut={(e) => (e.target.style.color = "var(--text-secondary)")}
        >
          Sign Out
        </button>
      </div>
    </header>
  );
}
