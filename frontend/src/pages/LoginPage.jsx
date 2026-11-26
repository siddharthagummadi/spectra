import React, { useState, useEffect } from "react";
import { useNavigate, Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { verifyCredentials, registerUser } from "../api/usersApi";
import { Button } from "../components/common/Button";
import { AlertBanner } from "../components/common/AlertBanner";

function SpectraLogo({ size = 28 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" fill="none" aria-hidden="true">
      <rect width="32" height="32" rx="8" fill="#526B86" />
      <path
        d="M7 18H11.5L14.5 10.5L18 21.5L21 15H25"
        stroke="#FFFFFF"
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function LoginPage({ initialTab = "signin" }) {
  const [searchParams] = useSearchParams();
  const requestedTab = searchParams.get("tab") === "register" ? "register" : initialTab;
  const [activeTab, setActiveTab] = useState(requestedTab);
  const [signinEmail, setSigninEmail] = useState("");
  const [signinPassword, setSigninPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [fullName, setFullName] = useState("");
  const [regEmail, setRegEmail] = useState("");
  const [regPassword, setRegPassword] = useState("");
  const [organizationCode, setOrganizationCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [fieldErrors, setFieldErrors] = useState(null);
  const [successMessage, setSuccessMessage] = useState("");

  useEffect(() => {
    setActiveTab(requestedTab);
  }, [requestedTab]);

  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSignIn = async (e) => {
    e.preventDefault();
    setErrorMessage("");
    setFieldErrors(null);
    setSuccessMessage("");
    setLoading(true);

    try {
      const userRes = await verifyCredentials(signinEmail, signinPassword);
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
        email: regEmail,
        password: regPassword,
        organizationCode: organizationCode.trim() || undefined
      });
      setSuccessMessage("Registration successful. Please sign in with your credentials.");
      setSigninEmail(regEmail);
      setSigninPassword("");
      setRegPassword("");
      setActiveTab("signin");
    } catch (err) {
      setErrorMessage(err.message || "Registration failed");
      setFieldErrors(err.fieldErrors || null);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-wrapper">
      {/* Left Desktop Showcase Panel */}
      <aside className="auth-showcase" aria-label="Spectra platform overview">
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12 }}>
          <Link to="/" style={{ display: "inline-flex", alignItems: "center", gap: 10, color: "#FFFFFF", textDecoration: "none" }}>
            <SpectraLogo size={30} />
            <span style={{ fontSize: 18, fontWeight: 700, letterSpacing: "0.05em" }}>SPECTRA</span>
          </Link>
          <Link
            to="/"
            style={{
              fontSize: 12,
              fontWeight: 600,
              color: "#E2E8F0",
              background: "rgba(255, 255, 255, 0.08)",
              border: "1px solid rgba(255, 255, 255, 0.16)",
              padding: "6px 12px",
              borderRadius: "999px",
              textDecoration: "none"
            }}
          >
            ← Platform Home
          </Link>
        </div>

        <div>
          <div style={{
            display: "inline-flex",
            alignItems: "center",
            gap: 6,
            padding: "4px 12px",
            borderRadius: "999px",
            backgroundColor: "rgba(255, 255, 255, 0.1)",
            border: "1px solid rgba(255, 255, 255, 0.16)",
            fontSize: 12,
            fontWeight: 600,
            color: "#E2E8F0",
            marginBottom: 16
          }}>
            ⚡ Enterprise AIOps &amp; Observability
          </div>

          <h1 style={{ color: "#FFFFFF", fontSize: 32, lineHeight: 1.2, fontWeight: 800, margin: "0 0 14px" }}>
            Unified Service Telemetry, Anomaly Detection &amp; AI RCA
          </h1>

          <p style={{ color: "#CBD5E1", fontSize: 15, lineHeight: 1.65, maxWidth: 480, margin: 0 }}>
            Sign in to monitor microservice fleets, inspect Z-score and IQR anomalies, manage
            incident workflows, and retrieve RAG-matched remediation runbooks.
          </p>

          <ul className="auth-feature-list">
            <li className="auth-feature-chip">
              <span>📊</span>
              <span>Fleet Telemetry</span>
            </li>
            <li className="auth-feature-chip">
              <span>⚡</span>
              <span>Z-Score &amp; IQR Alerts</span>
            </li>
            <li className="auth-feature-chip">
              <span>🧠</span>
              <span>Automated AI RCA</span>
            </li>
            <li className="auth-feature-chip">
              <span>📘</span>
              <span>RAG Runbook Search</span>
            </li>
          </ul>
        </div>

        <div className="auth-showcase-card">
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10, fontSize: 12 }}>
            <span style={{ fontWeight: 600, color: "#F8FAFC" }}>Connected Stack Architecture</span>
            <span style={{ color: "#86EFAC", fontWeight: 600 }}>● RBAC Protected</span>
          </div>
          <svg viewBox="0 0 380 54" style={{ width: "100%", height: "auto", display: "block", marginBottom: 10 }} aria-hidden="true">
            <path
              d="M 4 40 L 46 38 L 88 39 L 130 32 L 172 35 L 214 27 L 252 12 L 290 28 L 332 34 L 376 36"
              fill="none"
              stroke="#93C5FD"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
            <circle cx="252" cy="12" r="4.5" fill="#F87171" stroke="#1F2A37" strokeWidth="2" />
          </svg>
          <div style={{ display: "flex", justifyContent: "space-between", flexWrap: "wrap", gap: 8, fontSize: 11, color: "#CBD5E1" }}>
            <span>Spring Boot Core</span>
            <span>·</span>
            <span>FastAPI AI Service</span>
            <span>·</span>
            <span>Java Monitoring Agent</span>
          </div>
        </div>
      </aside>

      {/* Right Authentication Form Panel */}
      <main className="auth-form-panel">
        <div className="login-card">
          <div className="auth-mobile-brand">
            <div style={{ display: "inline-flex", alignItems: "center", gap: 8, marginBottom: 4 }}>
              <SpectraLogo size={26} />
              <span style={{ fontSize: 20, fontWeight: 800, letterSpacing: "0.04em", color: "var(--text-primary)" }}>SPECTRA</span>
            </div>
            <p style={{ fontSize: 12, color: "var(--text-secondary)", margin: 0 }}>
              Enterprise Operations &amp; Monitoring Platform
            </p>
          </div>

          <div style={{ marginBottom: 20 }}>
            <h2 style={{ fontSize: 21, fontWeight: 700, margin: "0 0 4px", color: "var(--text-primary)" }}>
              {activeTab === "signin" ? "Welcome back" : "Create your account"}
            </h2>
            <p style={{ fontSize: 13, color: "var(--text-secondary)", margin: 0 }}>
              {activeTab === "signin"
                ? "Sign in with your operator or administrator credentials."
                : "Register a standalone workspace or join an enterprise organization."}
            </p>
          </div>

          <div className="auth-tabs" role="tablist" aria-label="Authentication mode">
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === "signin"}
              className={`auth-tab-btn${activeTab === "signin" ? " is-active" : ""}`}
              onClick={() => { setActiveTab("signin"); setErrorMessage(""); setFieldErrors(null); }}
            >
              Sign In
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={activeTab === "register"}
              className={`auth-tab-btn${activeTab === "register" ? " is-active" : ""}`}
              onClick={() => { setActiveTab("register"); setErrorMessage(""); setFieldErrors(null); }}
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
                <div className="auth-input-wrap">
                  <span className="auth-input-icon" aria-hidden="true">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <rect x="2" y="4" width="20" height="16" rx="2" />
                      <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
                    </svg>
                  </span>
                  <input
                    id="signin-email"
                    type="email"
                    className="form-input auth-input-with-icon"
                    value={signinEmail}
                    onChange={(e) => setSigninEmail(e.target.value)}
                    required
                    autoComplete="email"
                    placeholder="Enter your email address"
                  />
                </div>
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="signin-password">Password</label>
                <div className="auth-input-wrap">
                  <span className="auth-input-icon" aria-hidden="true">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <rect x="3" y="11" width="18" height="11" rx="2" />
                      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                    </svg>
                  </span>
                  <input
                    id="signin-password"
                    type={showPassword ? "text" : "password"}
                    className="form-input auth-input-with-icon auth-input-with-toggle"
                    value={signinPassword}
                    onChange={(e) => setSigninPassword(e.target.value)}
                    required
                    autoComplete="current-password"
                    placeholder="Enter your password"
                  />
                  <button
                    type="button"
                    className="auth-password-toggle"
                    onClick={() => setShowPassword((prev) => !prev)}
                    aria-label={showPassword ? "Hide password" : "Show password"}
                  >
                    {showPassword ? "Hide" : "Show"}
                  </button>
                </div>
              </div>

              <div style={{ marginTop: 22 }}>
                <Button
                  type="submit"
                  variant="primary"
                  loading={loading}
                  style={{ width: "100%", justifyContent: "center", padding: "10px 16px", fontSize: 14, fontWeight: 600 }}
                >
                  Sign In to Platform
                </Button>
              </div>

              <p style={{ fontSize: 11, color: "var(--text-muted)", marginTop: 14, textAlign: "center" }}>
                Secure HTTP Basic credentials with role-based access control.
              </p>
            </form>
          ) : (
            <form onSubmit={handleRegister}>
              <div className="form-group">
                <label className="form-label" htmlFor="reg-name">Full Name</label>
                <div className="auth-input-wrap">
                  <span className="auth-input-icon" aria-hidden="true">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
                      <circle cx="12" cy="7" r="4" />
                    </svg>
                  </span>
                  <input
                    id="reg-name"
                    type="text"
                    className="form-input auth-input-with-icon"
                    value={fullName}
                    onChange={(e) => setFullName(e.target.value)}
                    required
                    maxLength={100}
                    placeholder="Alex Rivera"
                  />
                </div>
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="reg-email">Email Address</label>
                <div className="auth-input-wrap">
                  <span className="auth-input-icon" aria-hidden="true">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <rect x="2" y="4" width="20" height="16" rx="2" />
                      <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7" />
                    </svg>
                  </span>
                  <input
                    id="reg-email"
                    type="email"
                    className="form-input auth-input-with-icon"
                    value={regEmail}
                    onChange={(e) => setRegEmail(e.target.value)}
                    required
                    maxLength={150}
                    autoComplete="email"
                    placeholder="Enter your email address"
                  />
                </div>
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="reg-password">Password</label>
                <div className="auth-input-wrap">
                  <span className="auth-input-icon" aria-hidden="true">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <rect x="3" y="11" width="18" height="11" rx="2" />
                      <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                    </svg>
                  </span>
                  <input
                    id="reg-password"
                    type={showPassword ? "text" : "password"}
                    className="form-input auth-input-with-icon auth-input-with-toggle"
                    value={regPassword}
                    onChange={(e) => setRegPassword(e.target.value)}
                    required
                    minLength={8}
                    maxLength={72}
                    autoComplete="new-password"
                    placeholder="Create a password"
                  />
                  <button
                    type="button"
                    className="auth-password-toggle"
                    onClick={() => setShowPassword((prev) => !prev)}
                    aria-label={showPassword ? "Hide password" : "Show password"}
                  >
                    {showPassword ? "Hide" : "Show"}
                  </button>
                </div>
                <span style={{ fontSize: 11, color: "var(--text-muted)", display: "block", marginTop: 4 }}>
                  Must be 8 to 72 characters
                </span>
              </div>

              <div className="form-group">
                <label className="form-label" htmlFor="reg-org">Organization Code (Optional)</label>
                <div className="auth-input-wrap">
                  <span className="auth-input-icon" aria-hidden="true">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M6 22V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18Z" />
                      <path d="M6 12H4a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2" />
                      <path d="M18 9h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-2" />
                    </svg>
                  </span>
                  <input
                    id="reg-org"
                    type="text"
                    className="form-input auth-input-with-icon"
                    value={organizationCode}
                    onChange={(e) => setOrganizationCode(e.target.value)}
                    maxLength={64}
                    placeholder="e.g. ACME_PROD (optional)"
                  />
                </div>
              </div>

              <div style={{ marginTop: 22 }}>
                <Button
                  type="submit"
                  variant="primary"
                  loading={loading}
                  style={{ width: "100%", justifyContent: "center", padding: "10px 16px", fontSize: 14, fontWeight: 600 }}
                >
                  Create Account
                </Button>
              </div>
            </form>
          )}

          <div style={{ marginTop: 18, paddingTop: 14, borderTop: "1px solid var(--border-subtle)", textAlign: "center" }}>
            <Link to="/" style={{ fontSize: 12, color: "var(--text-secondary)", fontWeight: 500 }}>
              ← Back to Spectra Overview
            </Link>
          </div>
        </div>
      </main>
    </div>
  );
}
