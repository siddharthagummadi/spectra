import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { verifyCredentials, registerUser } from "../api/usersApi";
import { Button } from "../components/common/Button";
import { AlertBanner } from "../components/common/AlertBanner";

export function LoginPage() {
  const [activeTab, setActiveTab] = useState("signin");
  const [email, setEmail] = useState("operator@spectra.local");
  const [password, setPassword] = useState("Password123!");
  const [fullName, setFullName] = useState("");
  const [organizationCode, setOrganizationCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [fieldErrors, setFieldErrors] = useState(null);
  const [successMessage, setSuccessMessage] = useState("");

  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSignIn = async (e) => {
    e.preventDefault();
    setErrorMessage("");
    setFieldErrors(null);
    setSuccessMessage("");
    setLoading(true);

    try {
      const userRes = await verifyCredentials(email, password);
      login(
        {
          email: userRes.email,
          fullName: userRes.fullName,
          role: userRes.role,
          enterpriseId: userRes.enterpriseId,
          enterpriseName: userRes.enterpriseName,
          enterpriseOrganizationCode: userRes.enterpriseOrganizationCode,
          enterpriseStatus: userRes.enterpriseStatus
        },
        userRes.credentials
      );
      navigate(userRes.role === "ADMIN" ? "/admin/overview" : "/dashboard");
    } catch (err) {
      setErrorMessage(err.message || "Failed to authenticate with backend");
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    setErrorMessage("");
    setFieldErrors(null);
    setSuccessMessage("");
    setLoading(true);

    try {
      await registerUser({
        fullName,
        email,
        password,
        organizationCode: organizationCode.trim() || undefined
      });
      setSuccessMessage("Registration successful. Please sign in with your credentials.");
      setActiveTab("signin");
      setPassword("");
    } catch (err) {
      setErrorMessage(err.message || "Registration failed");
      setFieldErrors(err.fieldErrors || null);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      minHeight: "100vh",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      backgroundColor: "var(--bg-page)",
      padding: 20
    }}>
      <div style={{
        width: "100%",
        maxWidth: 420,
        backgroundColor: "var(--bg-surface)",
        border: "1px solid var(--border-default)",
        borderRadius: "var(--radius-md)",
        padding: 32,
        boxShadow: "var(--shadow-modal)"
      }}>
        <div style={{ textAlign: "center", marginBottom: 28 }}>
          <div style={{
            display: "inline-block",
            width: 14,
            height: 14,
            borderRadius: "50%",
            backgroundColor: "var(--accent-primary)",
            marginBottom: 8
          }}></div>
          <h1 style={{ fontSize: 22, fontWeight: 700, letterSpacing: "0.04em", margin: 0 }}>SPECTRA</h1>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 6, marginBottom: 0 }}>
            Enterprise Operations & Monitoring Platform
          </p>
        </div>

        <div style={{
          display: "flex",
          borderBottom: "1px solid var(--border-default)",
          marginBottom: 20
        }}>
          <button
            type="button"
            onClick={() => { setActiveTab("signin"); setErrorMessage(""); setFieldErrors(null); }}
            style={{
              flex: 1,
              padding: "10px 0",
              border: "none",
              borderBottom: activeTab === "signin" ? "2px solid var(--accent-primary)" : "2px solid transparent",
              background: "none",
              fontWeight: 600,
              fontSize: 13,
              color: activeTab === "signin" ? "var(--accent-primary)" : "var(--text-secondary)",
              cursor: "pointer"
            }}
          >
            Sign In
          </button>
          <button
            type="button"
            onClick={() => { setActiveTab("register"); setErrorMessage(""); setFieldErrors(null); }}
            style={{
              flex: 1,
              padding: "10px 0",
              border: "none",
              borderBottom: activeTab === "register" ? "2px solid var(--accent-primary)" : "2px solid transparent",
              background: "none",
              fontWeight: 600,
              fontSize: 13,
              color: activeTab === "register" ? "var(--accent-primary)" : "var(--text-secondary)",
              cursor: "pointer"
            }}
          >
            Register User
          </button>
        </div>

        {errorMessage && (
          <AlertBanner
            variant="error"
            message={errorMessage}
            fieldErrors={fieldErrors}
            onClose={() => setErrorMessage("")}
          />
        )}

        {successMessage && (
          <AlertBanner
            variant="success"
            message={successMessage}
            onClose={() => setSuccessMessage("")}
          />
        )}

        {activeTab === "signin" ? (
          <form onSubmit={handleSignIn}>
            <div className="form-group">
              <label className="form-label" htmlFor="signin-email">Email Address</label>
              <input
                id="signin-email"
                type="email"
                className="form-input"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoComplete="email"
              />
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="signin-password">Password</label>
              <input
                id="signin-password"
                type="password"
                className="form-input"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                autoComplete="current-password"
              />
            </div>

            <div style={{ marginTop: 24 }}>
              <Button type="submit" variant="primary" loading={loading} style={{ width: "100%", justifyContent: "center" }}>
                Sign In to Platform
              </Button>
            </div>

            <p style={{ fontSize: 11, color: "var(--text-muted)", marginTop: 16, textAlign: "center" }}>
              Secure HTTP Basic credentials with role-based access control.
            </p>
          </form>
        ) : (
          <form onSubmit={handleRegister}>
            <div className="form-group">
              <label className="form-label" htmlFor="reg-name">Full Name</label>
              <input
                id="reg-name"
                type="text"
                className="form-input"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                required
                maxLength={100}
              />
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="reg-email">Email Address</label>
              <input
                id="reg-email"
                type="email"
                className="form-input"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                maxLength={150}
              />
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="reg-password">Password</label>
              <input
                id="reg-password"
                type="password"
                className="form-input"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={8}
                maxLength={72}
              />
              <span style={{ fontSize: 11, color: "var(--text-muted)" }}>Must be 8 to 72 characters</span>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="reg-org">Organization Code (Optional)</label>
              <input
                id="reg-org"
                type="text"
                className="form-input"
                value={organizationCode}
                onChange={(e) => setOrganizationCode(e.target.value)}
                maxLength={64}
                placeholder="e.g. ACME_PROD (leave blank for standalone workspace)"
              />
            </div>

            <div style={{ marginTop: 24 }}>
              <Button type="submit" variant="primary" loading={loading} style={{ width: "100%", justifyContent: "center" }}>
                Create Account
              </Button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
