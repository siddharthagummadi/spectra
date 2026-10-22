import React, { useState, useEffect, useCallback, useMemo, useRef } from "react";
import { useAuth } from "../context/AuthContext";
import {
  getAllServices,
  createService,
  updateService,
  updateServiceStatus
} from "../api/servicesApi";
import { Card } from "../components/common/Card";
import { Badge } from "../components/common/Badge";
import { Button } from "../components/common/Button";
import { Modal } from "../components/common/Modal";
import { AlertBanner } from "../components/common/AlertBanner";
import { LoadingSpinner } from "../components/common/LoadingSpinner";
import { EmptyState } from "../components/common/EmptyState";

export function ServicesPage() {
  const { user, isAdmin } = useAuth();
  const [services, setServices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState("");
  const [search, setSearch] = useState("");
  const [envFilter, setEnvFilter] = useState("ALL");
  const [ownerFilter, setOwnerFilter] = useState("ALL");

  const [modalOpen, setModalOpen] = useState(false);
  const [editingService, setEditingService] = useState(null);
  const [formData, setFormData] = useState({
    name: "",
    description: "",
    endpointUrl: "",
    environment: "DEVELOPMENT"
  });
  const [modalLoading, setModalLoading] = useState(false);
  const [modalError, setModalError] = useState("");
  const [modalFieldErrors, setModalFieldErrors] = useState(null);

  const isRefreshingRef = useRef(false);

  const fetchServices = useCallback(async () => {
    if (isRefreshingRef.current) return;
    isRefreshingRef.current = true;
    setLoading(true);
    setErrorMessage("");
    try {
      const data = await getAllServices();
      setServices(data || []);
    } catch (err) {
      setErrorMessage(err.message || "Failed to load services");
    } finally {
      setLoading(false);
      isRefreshingRef.current = false;
    }
  }, []);

  useEffect(() => {
    fetchServices();
  }, [fetchServices]);

  useEffect(() => {
    const handleGlobalRefresh = () => {
      fetchServices();
    };
    window.addEventListener("spectra:refresh", handleGlobalRefresh);
    return () => window.removeEventListener("spectra:refresh", handleGlobalRefresh);
  }, [fetchServices]);

  const uniqueOwners = useMemo(() => {
    const set = new Set();
    services.forEach((s) => {
      if (s.ownerEmail) set.add(s.ownerEmail);
    });
    return Array.from(set).sort();
  }, [services]);

  const handleOpenCreate = () => {
    setEditingService(null);
    setFormData({
      name: "",
      description: "",
      endpointUrl: "https://",
      environment: "DEVELOPMENT"
    });
    setModalError("");
    setModalFieldErrors(null);
    setModalOpen(true);
  };

  const handleOpenEdit = (svc) => {
    setEditingService(svc);
    setFormData({
      name: svc.name,
      description: svc.description || "",
      endpointUrl: svc.endpointUrl,
      environment: svc.environment
    });
    setModalError("");
    setModalFieldErrors(null);
    setModalOpen(true);
  };

  const handleSubmitModal = async (e) => {
    e.preventDefault();
    setModalLoading(true);
    setModalError("");
    setModalFieldErrors(null);

    try {
      if (editingService) {
        await updateService(editingService.id, formData);
      } else {
        await createService(formData);
      }
      setModalOpen(false);
      fetchServices();
    } catch (err) {
      setModalError(err.message || "Failed to save service");
      setModalFieldErrors(err.fieldErrors || null);
    } finally {
      setModalLoading(false);
    }
  };

  const handleToggleStatus = async (svc) => {
    try {
      await updateServiceStatus(svc.id, !svc.active);
      fetchServices();
    } catch (err) {
      setErrorMessage(err.message || "Failed to update service status");
    }
  };

  const filteredServices = services.filter((s) => {
    const q = search.trim().toLowerCase();
    const matchesSearch = !q ||
      s.name.toLowerCase().includes(q) ||
      (s.description && s.description.toLowerCase().includes(q)) ||
      (s.ownerEmail && s.ownerEmail.toLowerCase().includes(q));
    const matchesEnv = envFilter === "ALL" || s.environment === envFilter;
    const matchesOwner = !isAdmin || ownerFilter === "ALL" || s.ownerEmail === ownerFilter;
    return matchesSearch && matchesEnv && matchesOwner;
  });

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 20 }}>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 700 }}>Monitored Services</h1>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 2 }}>
            {isAdmin
              ? "All registered monitored services across user workspaces"
              : "Manage registered application services and monitoring endpoints"}
          </p>
        </div>
        <Button variant="primary" onClick={handleOpenCreate}>
          + Register Service
        </Button>
      </div>

      {errorMessage && (
        <AlertBanner message={errorMessage} onClose={() => setErrorMessage("")} />
      )}

      <Card>
        <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
          <input
            type="text"
            className="form-input"
            placeholder={isAdmin ? "Search services or owners..." : "Search services by name or description..."}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{ maxWidth: 320 }}
          />

          {isAdmin && (
            <select
              className="form-select"
              value={ownerFilter}
              onChange={(e) => setOwnerFilter(e.target.value)}
              style={{ width: 200 }}
            >
              <option value="ALL">All Owners</option>
              {uniqueOwners.map((owner) => (
                <option key={owner} value={owner}>
                  {owner}
                </option>
              ))}
            </select>
          )}

          <select
            className="form-select"
            value={envFilter}
            onChange={(e) => setEnvFilter(e.target.value)}
            style={{ width: 180 }}
          >
            <option value="ALL">All Environments</option>
            <option value="DEVELOPMENT">DEVELOPMENT</option>
            <option value="STAGING">STAGING</option>
            <option value="PRODUCTION">PRODUCTION</option>
          </select>

          <Button
            variant="secondary"
            onClick={fetchServices}
            loading={loading}
            disabled={loading}
            style={{ marginLeft: "auto" }}
          >
            Refresh
          </Button>
        </div>

        {loading ? (
          <LoadingSpinner message="Loading services..." />
        ) : filteredServices.length === 0 ? (
          <EmptyState
            title="No services found"
            description={services.length === 0 ? "Get started by registering your first monitored service." : "No services match your search or filter."}
            action={services.length === 0 && (
              <Button variant="primary" onClick={handleOpenCreate}>Register Service</Button>
            )}
          />
        ) : (
          <table>
            <thead>
              <tr>
                <th>Service Name</th>
                {isAdmin && <th>Owner</th>}
                <th>Environment</th>
                <th>Endpoint URL</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredServices.map((svc) => {
                const isOwner = !isAdmin || !svc.ownerEmail || svc.ownerEmail === user?.email;
                return (
                  <tr key={svc.id}>
                    <td>
                      <div style={{ fontWeight: 600 }}>{svc.name}</div>
                      {svc.description && (
                        <div style={{ fontSize: 12, color: "var(--text-secondary)", marginTop: 2 }}>
                          {svc.description}
                        </div>
                      )}
                    </td>
                    {isAdmin && (
                      <td>
                        <code style={{ fontSize: 12, color: "var(--text-primary)" }}>
                          {svc.ownerEmail || "System"}
                        </code>
                      </td>
                    )}
                    <td>
                      <span style={{ fontSize: 12, fontWeight: 500, color: "var(--text-secondary)" }}>
                        {svc.environment}
                      </span>
                    </td>
                    <td>
                      <code style={{ fontSize: 12, color: "var(--text-muted)" }}>{svc.endpointUrl}</code>
                    </td>
                    <td>
                      <Badge variant={svc.active ? "healthy" : "neutral"}>
                        {svc.active ? "ACTIVE" : "INACTIVE"}
                      </Badge>
                    </td>
                    <td>
                      {isOwner ? (
                        <div style={{ display: "flex", gap: 8 }}>
                          <button
                            onClick={() => handleOpenEdit(svc)}
                            style={{
                              background: "none",
                              border: "none",
                              color: "var(--accent-primary)",
                              fontSize: 12,
                              fontWeight: 500,
                              cursor: "pointer"
                            }}
                          >
                            Edit
                          </button>
                          <button
                            onClick={() => handleToggleStatus(svc)}
                            style={{
                              background: "none",
                              border: "none",
                              color: svc.active ? "var(--status-warning)" : "var(--status-healthy)",
                              fontSize: 12,
                              fontWeight: 500,
                              cursor: "pointer"
                            }}
                          >
                            {svc.active ? "Deactivate" : "Activate"}
                          </button>
                        </div>
                      ) : (
                        <span style={{ fontSize: 12, color: "var(--text-muted)", fontStyle: "italic" }}>
                          Read-only
                        </span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </Card>

      <Modal
        isOpen={modalOpen}
        title={editingService ? `Edit Service: ${editingService.name}` : "Register New Monitored Service"}
        onClose={() => setModalOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button variant="primary" loading={modalLoading} onClick={handleSubmitModal}>
              {editingService ? "Save Changes" : "Register Service"}
            </Button>
          </>
        }
      >
        {modalError && (
          <AlertBanner
            variant="error"
            message={modalError}
            fieldErrors={modalFieldErrors}
            onClose={() => setModalError("")}
          />
        )}

        <form onSubmit={handleSubmitModal}>
          <div className="form-group">
            <label className="form-label" htmlFor="svc-name">Service Name *</label>
            <input
              id="svc-name"
              type="text"
              className="form-input"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              required
              maxLength={100}
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="svc-desc">Description</label>
            <input
              id="svc-desc"
              type="text"
              className="form-input"
              value={formData.description}
              onChange={(e) => setFormData({ ...formData, description: e.target.value })}
              maxLength={255}
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="svc-url">Endpoint URL *</label>
            <input
              id="svc-url"
              type="url"
              className="form-input"
              value={formData.endpointUrl}
              onChange={(e) => setFormData({ ...formData, endpointUrl: e.target.value })}
              required
              placeholder="https://service.example.com/health"
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="svc-env">Environment *</label>
            <select
              id="svc-env"
              className="form-select"
              value={formData.environment}
              onChange={(e) => setFormData({ ...formData, environment: e.target.value })}
              required
            >
              <option value="DEVELOPMENT">DEVELOPMENT</option>
              <option value="STAGING">STAGING</option>
              <option value="PRODUCTION">PRODUCTION</option>
            </select>
          </div>
        </form>
      </Modal>
    </div>
  );
}
