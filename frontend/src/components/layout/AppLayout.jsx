import React, { useState, useEffect } from "react";
import { Outlet, useLocation } from "react-router-dom";
import { Sidebar } from "./Sidebar";
import { Header } from "./Header";

export function AppLayout() {
  const location = useLocation();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // Close mobile navigation drawer automatically on route change
  useEffect(() => {
    setSidebarOpen(false);
  }, [location.pathname]);

  // Close mobile navigation drawer on Escape key
  useEffect(() => {
    if (!sidebarOpen) return;
    const handleKeyDown = (e) => {
      if (e.key === "Escape") {
        setSidebarOpen(false);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [sidebarOpen]);

  const getTitle = () => {
    const path = location.pathname;
    if (path.startsWith("/dashboard")) return "Operations Dashboard";
    if (path.startsWith("/services")) return "Monitored Services";
    if (path.startsWith("/metrics")) return "Telemetry & Metrics";
    if (path.startsWith("/alerts")) return "Alert Management";
    if (path.startsWith("/incidents/")) return "Incident Diagnostics & Runbook";
    if (path.startsWith("/incidents")) return "Incident Management";
    if (path.startsWith("/benchmark")) return "Research Benchmark Evaluation";
    return "Spectra Operations Platform";
  };

  return (
    <div className="app-shell">
      <div
        className={`sidebar-backdrop${sidebarOpen ? " is-open" : ""}`}
        onClick={() => setSidebarOpen(false)}
        aria-hidden="true"
      />
      <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <div className="app-main-wrap">
        <Header
          title={getTitle()}
          isSidebarOpen={sidebarOpen}
          onMenuClick={() => setSidebarOpen((prev) => !prev)}
        />
        <main className="app-main">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
