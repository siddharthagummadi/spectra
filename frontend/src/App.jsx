import React from "react";
import { Routes, Route, Navigate } from "react-router-dom";
import { useAuth } from "./context/AuthContext";
import { LoginPage } from "./pages/LoginPage";
import { AppLayout } from "./components/layout/AppLayout";
import { DashboardPage } from "./pages/DashboardPage";
import { ServicesPage } from "./pages/ServicesPage";
import { MetricsPage } from "./pages/MetricsPage";
import { AlertsPage } from "./pages/AlertsPage";
import { IncidentsPage } from "./pages/IncidentsPage";
import { IncidentDetailPage } from "./pages/IncidentDetailPage";
import { IngestionPage } from "./pages/IngestionPage";
import { DatasetManagementPage } from "./pages/DatasetManagementPage";
import { EvaluationPage } from "./pages/EvaluationPage";
import { IntegrationsPage } from "./pages/IntegrationsPage";
import { AiAnalyticsPage } from "./pages/AiAnalyticsPage";
import { AdminOverviewPage } from "./pages/AdminOverviewPage";
import { AdminEnterprisesPage } from "./pages/AdminEnterprisesPage";
import { AdminEnterpriseDetailPage } from "./pages/AdminEnterpriseDetailPage";
import { AdminAgentsPage } from "./pages/AdminAgentsPage";
import { AuditLogsPage } from "./pages/AuditLogsPage";
import { EnterpriseAgentsPage } from "./pages/EnterpriseAgentsPage";

function ProtectedRoute({ children }) {
  const { isAuthenticated, isInitializing } = useAuth();

  if (isInitializing) {
    return (
      <div style={{
        minHeight: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        backgroundColor: "var(--bg-page)",
        color: "var(--text-secondary)",
        fontSize: 14
      }}>
        Initializing Spectra Session...
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return children;
}

function AdminRoute({ children }) {
  const { isAdmin } = useAuth();
  if (!isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }
  return children;
}

function EnterpriseUserRoute({ children }) {
  const { isAdmin } = useAuth();
  if (isAdmin) {
    return <Navigate to="/admin/overview" replace />;
  }
  return children;
}

export function App() {
  const { isAuthenticated, isInitializing, isAdmin } = useAuth();

  return (
    <Routes>
      <Route
        path="/login"
        element={!isInitializing && isAuthenticated ? <Navigate to={isAdmin ? "/admin/overview" : "/dashboard"} replace /> : <LoginPage />}
      />

      <Route
        path="/"
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to={isAdmin ? "/admin/overview" : "/dashboard"} replace />} />

        {/* Platform Admin Routes */}
        <Route path="admin/overview" element={<AdminRoute><AdminOverviewPage /></AdminRoute>} />
        <Route path="admin/enterprises" element={<AdminRoute><AdminEnterprisesPage /></AdminRoute>} />
        <Route path="admin/enterprises/:enterpriseId" element={<AdminRoute><AdminEnterpriseDetailPage /></AdminRoute>} />
        <Route path="admin/agents" element={<AdminRoute><AdminAgentsPage /></AdminRoute>} />
        <Route path="admin/audit-logs" element={<AdminRoute><AuditLogsPage /></AdminRoute>} />

        {/* Enterprise User Routes */}
        <Route path="dashboard" element={<EnterpriseUserRoute><DashboardPage /></EnterpriseUserRoute>} />
        <Route path="agents" element={<EnterpriseUserRoute><EnterpriseAgentsPage /></EnterpriseUserRoute>} />
        <Route path="ingestion" element={<EnterpriseUserRoute><IngestionPage /></EnterpriseUserRoute>} />
        <Route path="datasets" element={<EnterpriseUserRoute><DatasetManagementPage /></EnterpriseUserRoute>} />
        <Route path="evaluation" element={<EnterpriseUserRoute><EvaluationPage /></EnterpriseUserRoute>} />
        <Route path="benchmark" element={<EnterpriseUserRoute><EvaluationPage /></EnterpriseUserRoute>} />

        {/* Shared Read / Role-Aware Views */}
        <Route path="integrations" element={<IntegrationsPage />} />
        <Route path="services" element={<ServicesPage />} />
        <Route path="metrics" element={<MetricsPage />} />
        <Route path="alerts" element={<AlertsPage />} />
        <Route path="incidents" element={<IncidentsPage />} />
        <Route path="incidents/:id" element={<IncidentDetailPage />} />
        <Route path="analytics" element={<AiAnalyticsPage />} />
        <Route path="ai-analytics" element={<Navigate to="/analytics" replace />} />
        <Route path="copilot" element={<Navigate to="/incidents" replace />} />
      </Route>

      <Route path="*" element={<Navigate to={isAdmin ? "/admin/overview" : "/dashboard"} replace />} />
    </Routes>
  );
}
