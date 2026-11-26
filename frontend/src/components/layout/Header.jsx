import React, { useState, useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import { checkBackendHealth } from "../../api/usersApi";
import { getAiHealth } from "../../api/aiApi";
import { getNotifications, getUnreadCount, markAsRead } from "../../api/notificationsApi";
import { Badge } from "../common/Badge";
import { useAuth } from "../../context/AuthContext";

export function Header({ title }) {
  const [healthStatus, setHealthStatus] = useState("CHECKING");
  const [aiStatus, setAiStatus] = useState("CHECKING");
  const [refreshing, setRefreshing] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [notifications, setNotifications] = useState([]);
  const [showNotifications, setShowNotifications] = useState(false);

  const { user, logout, isAdmin } = useAuth();
  const notifDropdownRef = useRef(null);
  const refreshTimerRef = useRef(null);

  useEffect(() => {
    return () => {
      if (refreshTimerRef.current) {
        clearTimeout(refreshTimerRef.current);
      }
    };
  }, []);

  const fetchHealth = () => {
    const p1 = checkBackendHealth()
      .then((res) => setHealthStatus(res?.status === "UP" ? "UP" : "DEGRADED"))
      .catch(() => setHealthStatus("UNAVAILABLE"));

    const p2 = getAiHealth()
      .then((res) => setAiStatus(res?.status === "UP" ? "UP" : "STANDBY"))
      .catch(() => setAiStatus("STANDBY"));

    return Promise.allSettled([p1, p2]);
  };

  const fetchNotifications = async () => {
    try {
      const countRes = await getUnreadCount();
      setUnreadCount(countRes?.count || 0);

      const listRes = await getNotifications();
      setNotifications(Array.isArray(listRes) ? listRes.slice(0, 10) : []);
    } catch {
      // Ignore notification fetch errors silently
    }
  };

  useEffect(() => {
    fetchHealth();
    fetchNotifications();

    const interval = setInterval(() => {
      fetchNotifications();
    }, 15000);

    return () => clearInterval(interval);
  }, []);

  // Close notifications popover on click outside
  useEffect(() => {
    function handleClickOutside(event) {
      if (notifDropdownRef.current && !notifDropdownRef.current.contains(event.target)) {
        setShowNotifications(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleRefresh = async () => {
    if (refreshing) return;
    setRefreshing(true);
    try {
      if (typeof window !== "undefined") {
        window.dispatchEvent(new CustomEvent("spectra:refresh"));
      }
      await fetchHealth();
      await fetchNotifications();
    } finally {
      refreshTimerRef.current = setTimeout(() => {
        setRefreshing(false);
      }, 400);
    }
  };

  const handleMarkAsRead = async (notifId, e) => {
    e.stopPropagation();
    try {
      await markAsRead(notifId);
      setNotifications((prev) =>
        prev.map((n) => (n.id === notifId ? { ...n, read: true } : n))
      );
      setUnreadCount((prev) => Math.max(0, prev - 1));
    } catch (err) {
      console.warn("Failed to mark notification as read:", err);
    }
  };

  return (
    <header style={{
      minHeight: 52,
      backgroundColor: "var(--bg-surface)",
      borderBottom: "1px solid var(--border-default)",
      display: "flex",
      alignItems: "center",
      justifyContent: "space-between",
      padding: "8px 20px",
      flexWrap: "wrap",
      gap: 12,
      boxSizing: "border-box",
      position: "relative",
      zIndex: 40
    }}>
      <div style={{ display: "flex", alignItems: "center", gap: 12, flexShrink: 0 }}>
        <div style={{ fontSize: 15, fontWeight: 700, color: "var(--text-primary)" }}>
          {title || "Spectra Operations Platform"}
        </div>
        <button
          type="button"
          onClick={handleRefresh}
          disabled={refreshing}
          style={{
            background: "none",
            border: "1px solid var(--border-default)",
            color: "var(--text-secondary)",
            fontSize: 11,
            cursor: refreshing ? "not-allowed" : "pointer",
            padding: "4px 9px",
            borderRadius: "var(--radius-sm)"
          }}
        >
          {refreshing ? "Refreshing..." : "↻ Refresh"}
        </button>

        {!isAdmin && (
          <Link
            to="/ingestion"
            style={{
              background: "rgba(59, 130, 246, 0.08)",
              border: "1px solid rgba(59, 130, 246, 0.3)",
              color: "var(--accent-primary)",
              fontSize: 11,
              fontWeight: 600,
              textDecoration: "none",
              padding: "4px 10px",
              borderRadius: "var(--radius-sm)",
              display: "flex",
              alignItems: "center",
              gap: 4
            }}
            title="Telemetry Data Ingestion Hub"
          >
            <span>📥</span>
            <span>Ingest Data</span>
          </Link>
        )}
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap", justifyContent: "flex-end" }}>
        <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 11 }}>
          <span title={`Backend API: ${healthStatus}`}>
            <Badge variant={healthStatus === "UP" ? "healthy" : "critical"}>API: {healthStatus}</Badge>
          </span>
          <span title={`AI Intelligence: ${aiStatus}`}>
            <Badge variant={aiStatus === "UP" ? "healthy" : "neutral"}>AI: {aiStatus === "UP" ? "ONLINE" : aiStatus}</Badge>
          </span>
        </div>

        {!isAdmin && (
          <Link
            to="/evaluation"
            title="Automated Model Evaluation & Confusion Matrix"
            style={{
              display: "inline-flex",
              alignItems: "center",
              gap: 4,
              fontSize: 11,
              fontWeight: 600,
              color: "var(--status-healthy, #10b981)",
              textDecoration: "none",
              backgroundColor: "rgba(16, 185, 129, 0.08)",
              padding: "4px 9px",
              borderRadius: "var(--radius-sm)",
              border: "1px solid rgba(16, 185, 129, 0.25)"
            }}
          >
            <span>📊</span>
            <span>Evaluation</span>
          </Link>
        )}

        {/* In-App Notifications Bell */}
        <div style={{ position: "relative" }} ref={notifDropdownRef}>
          <button
            type="button"
            onClick={() => setShowNotifications((prev) => !prev)}
            style={{
              background: "none",
              border: "1px solid var(--border-default)",
              borderRadius: "var(--radius-sm)",
              padding: "4px 8px",
              cursor: "pointer",
              position: "relative",
              display: "flex",
              alignItems: "center",
              fontSize: 13,
              color: "var(--text-primary)"
            }}
            title="In-app Notifications"
          >
            🔔
            {unreadCount > 0 && (
              <span style={{
                position: "absolute",
                top: -5,
                right: -6,
                backgroundColor: "var(--status-critical, #ef4444)",
                color: "#fff",
                fontSize: 10,
                fontWeight: 700,
                borderRadius: "50%",
                width: 16,
                height: 16,
                display: "flex",
                alignItems: "center",
                justifyContent: "center"
              }}>
                {unreadCount > 9 ? "9+" : unreadCount}
              </span>
            )}
          </button>

          {showNotifications && (
            <div style={{
              position: "absolute",
              top: "100%",
              right: 0,
              marginTop: 6,
              width: 320,
              backgroundColor: "var(--bg-surface)",
              border: "1px solid var(--border-default)",
              borderRadius: "var(--radius-md)",
              boxShadow: "var(--shadow-modal)",
              padding: 12,
              zIndex: 100,
              maxHeight: 380,
              overflowY: "auto"
            }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
                <span style={{ fontSize: 13, fontWeight: 700, color: "var(--text-primary)" }}>Notifications</span>
                {unreadCount > 0 && (
                  <span style={{ fontSize: 11, color: "var(--accent-primary)" }}>{unreadCount} unread</span>
                )}
              </div>

              {notifications.length === 0 ? (
                <div style={{ fontSize: 12, color: "var(--text-secondary)", textAlign: "center", padding: "16px 0" }}>
                  No notifications yet
                </div>
              ) : (
                notifications.map((n) => (
                  <div
                    key={n.id}
                    style={{
                      padding: "8px 10px",
                      borderRadius: "var(--radius-sm)",
                      backgroundColor: n.read ? "transparent" : "var(--bg-subtle)",
                      borderBottom: "1px solid var(--border-subtle)",
                      marginBottom: 6,
                      fontSize: 12
                    }}
                  >
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 6 }}>
                      <span style={{ fontWeight: n.read ? 500 : 700, color: "var(--text-primary)" }}>
                        {n.title}
                      </span>
                      {!n.read && (
                        <button
                          type="button"
                          onClick={(e) => handleMarkAsRead(n.id, e)}
                          style={{
                            background: "none",
                            border: "none",
                            color: "var(--accent-primary)",
                            fontSize: 10,
                            cursor: "pointer",
                            padding: 0
                          }}
                        >
                          Mark read
                        </button>
                      )}
                    </div>
                    <div style={{ fontSize: 11, color: "var(--text-secondary)", marginTop: 3 }}>
                      {n.message}
                    </div>
                  </div>
                ))
              )}
            </div>
          )}
        </div>

        <div style={{ width: 1, height: 16, backgroundColor: "var(--border-default)" }}></div>

        <div style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 12, color: "var(--text-secondary)" }}>
          <span style={{ maxWidth: 140, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }} title={user?.email}>
            {user?.email}
          </span>
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
            fontSize: 12,
            cursor: "pointer",
            padding: "2px 6px",
            borderRadius: "var(--radius-sm)"
          }}
        >
          Sign Out
        </button>
      </div>
    </header>
  );
}
