import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { verifyCredentials, registerUser } from "../api/usersApi";
import { Button } from "../components/common/Button";
import { AlertBanner } from "../components/common/AlertBanner";

export function LoginPage() {
  const [activeTab, setActiveTab] = useState("signin");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
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
      login({ email: userRes.email, fullName: userRes.fullName, role: userRes.role }, userRes.credentials);
      navigate("/dashboard");
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
      await registerUser({ fullName, email, password });
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
        <div style={{ textAlign: "center", marginBottom: 24 }}>
          <div style={{
            display: "inline-block",
            width: 12,
            height: 12,
            borderRadius: "50%",
            backgroundColor: "var(--accent-primary)",
            marginBottom: 8
          }}></div>
          <h1 style={{ fontSize: 20, fontWeight: 700, letterSpacing: "0.04em" }}>SPECTRA</h1>
          <p style={{ fontSize: 13, color: "var(--text-secondary)", marginTop: 4 }}>
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

            <div style={{ marginTop: 20 }}>
              <Button type="submit" variant="primary" loading={loading} style={{ width: "100%" }}>
                Sign In to Platform
              </Button>
            </div>

            <p style={{ fontSize: 11, color: "var(--text-muted)", marginTop: 14, textAlign: "center" }}>
              HTTP Basic credentials are kept in React memory for security.
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

            <div style={{ marginTop: 20 }}>
              <Button type="submit" variant="primary" loading={loading} style={{ width: "100%" }}>
                Create Account
              </Button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
