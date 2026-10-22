import React from "react";
import { Outlet, useLocation } from "react-router-dom";
import { Sidebar } from "./Sidebar";
import { Header } from "./Header";

export function AppLayout() {
  const location = useLocation();

  const getTitle = () => {
    const path = location.pathname;
    if (path.startsWith("/dashboard")) return "Operations Dashboard";
    if (path.startsWith("/services")) return "Monitored Services";
    if (path.startsWith("/metrics")) return "Telemetry & Metrics";
    if (path.startsWith("/alerts")) return "Alert Management";
    if (path.startsWith("/incidents/")) return "Incident Details";
    if (path.startsWith("/incidents")) return "Incident Management";
    return "Spectra Operations Platform";
  };

  return (
    <div style={{ display: "flex", minHeight: "100vh", backgroundColor: "var(--bg-page)" }}>
      <Sidebar />
      <div style={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}>
        <Header title={getTitle()} />
        <main style={{ flex: 1, padding: "24px 28px", maxWidth: 1440, width: "100%", margin: "0 auto", boxSizing: "border-box" }}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
