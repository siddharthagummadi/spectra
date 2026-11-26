import React, { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import "../styles/landing.css";

function SpectraLogoMark({ size = 26 }) {
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

function useReveal() {
  const ref = useRef(null);
  const [visible, setVisible] = useState(
    () => typeof window === "undefined" || typeof IntersectionObserver === "undefined"
  );

  useEffect(() => {
    if (visible || !ref.current || typeof IntersectionObserver === "undefined") {
      setVisible(true);
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            setVisible(true);
            observer.disconnect();
          }
        });
      },
      { threshold: 0.12 }
    );
    observer.observe(ref.current);
    return () => observer.disconnect();
  }, [visible]);

  return { ref, className: `reveal-block${visible ? " is-visible" : ""}` };
}

function RevealSection({ id, className = "", children }) {
  const reveal = useReveal();
  return (
    <section
      id={id}
      ref={reveal.ref}
      className={`${className} ${reveal.className}`.trim()}
    >
      {children}
    </section>
  );
}

const NAV_LINKS = [
  { id: "home", label: "Home" },
  { id: "about", label: "About" },
  { id: "features", label: "Features" },
  { id: "how-it-works", label: "How It Works" },
  { id: "contact", label: "Contact" }
];

const KEY_FEATURES = [
  {
    title: "Microservice Fleet Monitoring",
    description:
      "Track real-time service health, active status, and operational metrics across Development, Staging, Canary, and Production environments.",
    icon: (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <rect x="2" y="3" width="20" height="14" rx="2" />
        <path d="M8 21h8M12 17v4" />
        <path d="M6 10h3l2-3 2 6 2-3h3" />
      </svg>
    )
  },
  {
    title: "High-Frequency Telemetry Ingestion",
    description:
      "Stream CPU usage, memory usage, response time, and error rates via the standalone Java agent, batch datasets, or enterprise connectors.",
    icon: (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
        <polyline points="7 10 12 15 17 10" />
        <line x1="12" y1="15" x2="12" y2="3" />
      </svg>
    )
  },
  {
    title: "Statistical & ML Anomaly Detection",
    description:
      "Combine configurable threshold alert rules with Z-score and IQR statistical analysis to detect abnormal service behavior early.",
    icon: (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
        <line x1="12" y1="9" x2="12" y2="13" />
        <line x1="12" y1="17" x2="12.01" y2="17" />
      </svg>
    )
  },
  {
    title: "AI Root Cause Analysis (RCA)",
    description:
      "Automatically correlate multi-metric breach signatures, alerts, and incident context to diagnose primary root causes with confidence scores.",
    icon: (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="11" cy="11" r="8" />
        <line x1="21" y1="21" x2="16.65" y2="16.65" />
        <path d="M11 8v6M8 11h6" />
      </svg>
    )
  },
  {
    title: "Runbook RAG & Predictive Ops",
    description:
      "Retrieve actionable remediation runbooks matched to incident symptoms and review failure forecasts and explainable recommendations.",
    icon: (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
        <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
      </svg>
    )
  },
  {
    title: "Incident Lifecycle & RBAC Governance",
    description:
      "Manage incidents from OPEN through INVESTIGATING to RESOLVED and CLOSED with engineer notes, audit logs, and role-based access control.",
    icon: (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
        <path d="m9 12 2 2 4-4" />
      </svg>
    )
  }
];

const HOW_IT_WORKS_STEPS = [
  {
    step: "01",
    title: "Connect & Stream Telemetry",
    description:
      "Register your services or deploy the Java monitoring agent and external connectors (Prometheus, OpenTelemetry, Kafka, Datadog, or CloudWatch) to ingest live metrics."
  },
  {
    step: "02",
    title: "Evaluate Rules & Detect Anomalies",
    description:
      "The Spring Boot core evaluates custom alert thresholds while the FastAPI AI service runs Z-score and IQR anomaly detection and forecasting."
  },
  {
    step: "03",
    title: "Diagnose Root Cause",
    description:
      "When incidents trigger, Spectra correlates metric signatures and active alerts to surface probable root causes and contributing factors."
  },
  {
    step: "04",
    title: "Remediate & Track Resolution",
    description:
      "Follow RAG-matched runbook steps, log investigation notes, and transition incidents cleanly through to resolution and benchmark evaluation."
  }
];

const WHY_SPECTRA_ITEMS = [
  {
    title: "Decoupled 4-Tier Architecture",
    description:
      "Built cleanly across a React 18 dashboard, Spring Boot 4 core API, PostgreSQL data store, FastAPI AI microservice, and standalone Java monitoring agent.",
    icon: (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <polygon points="12 2 2 7 12 12 22 7 12 2" />
        <polyline points="2 17 12 22 22 17" />
        <polyline points="2 12 12 17 22 12" />
      </svg>
    )
  },
  {
    title: "Rules + Statistical Intelligence Together",
    description:
      "Pairs deterministic threshold alerting with Z-score/IQR statistical detection, predictive forecasting, and retrieval-augmented runbooks in one workflow.",
    icon: (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <line x1="18" y1="20" x2="18" y2="10" />
        <line x1="12" y1="20" x2="12" y2="4" />
        <line x1="6" y1="20" x2="6" y2="14" />
      </svg>
    )
  },
  {
    title: "Transparent Model Evaluation",
    description:
      "Dedicated evaluation tooling tracks precision, recall, F1 score, and 2×2 confusion matrix outcomes across fault scenarios instead of black-box guesses.",
    icon: (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="12" cy="12" r="10" />
        <circle cx="12" cy="12" r="6" />
        <circle cx="12" cy="12" r="2" />
      </svg>
    )
  },
  {
    title: "Multi-Enterprise Ready & Portable",
    description:
      "Supports both Platform Administrators and Enterprise Operators with Spring Security RBAC, plus Docker Compose, Prometheus, Render, and Vercel support.",
    icon: (
      <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
        <path d="M7 11V7a5 5 0 0 1 10 0v4" />
      </svg>
    )
  }
];

export function LandingPage() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [contactName, setContactName] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [contactMessage, setContactMessage] = useState("");
  const [contactSent, setContactSent] = useState(false);

  const handleNavClick = (e, sectionId) => {
    setMobileMenuOpen(false);
    const target = document.getElementById(sectionId);
    if (target && typeof target.scrollIntoView === "function") {
      e.preventDefault();
      target.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  };

  const handleContactSubmit = (e) => {
    e.preventDefault();
    setContactSent(true);
    setContactName("");
    setContactEmail("");
    setContactMessage("");
  };

  return (
    <div className="landing-page">
      {/* Sticky Navigation Bar */}
      <header className="landing-nav">
        <div className="landing-nav-inner">
          <a
            href="#home"
            className="landing-brand"
            onClick={(e) => handleNavClick(e, "home")}
          >
            <SpectraLogoMark size={28} />
            <span>SPECTRA</span>
          </a>

          <nav className="landing-nav-links" aria-label="Landing navigation">
            {NAV_LINKS.map((item) => (
              <a
                key={item.id}
                href={`#${item.id}`}
                className="landing-nav-link"
                onClick={(e) => handleNavClick(e, item.id)}
              >
                {item.label}
              </a>
            ))}
          </nav>

          <div className="landing-nav-actions">
            <Link to="/login" className="landing-btn landing-btn-ghost">
              Sign In
            </Link>
            <Link to="/register" className="landing-btn landing-btn-primary">
              Sign Up
            </Link>
          </div>

          <button
            type="button"
            className="landing-mobile-toggle"
            aria-label="Toggle landing navigation menu"
            aria-expanded={mobileMenuOpen}
            onClick={() => setMobileMenuOpen((prev) => !prev)}
          >
            {mobileMenuOpen ? "✕" : "☰"}
          </button>
        </div>

        {/* Mobile Nav Drawer */}
        <div className={`landing-mobile-drawer${mobileMenuOpen ? " is-open" : ""}`}>
          {NAV_LINKS.map((item) => (
            <a
              key={item.id}
              href={`#${item.id}`}
              className="landing-nav-link"
              style={{ padding: "6px 0" }}
              onClick={(e) => handleNavClick(e, item.id)}
            >
              {item.label}
            </a>
          ))}
          <div className="landing-mobile-actions">
            <Link
              to="/login"
              className="landing-btn landing-btn-secondary"
              onClick={() => setMobileMenuOpen(false)}
            >
              Sign In
            </Link>
            <Link
              to="/register"
              className="landing-btn landing-btn-primary"
              onClick={() => setMobileMenuOpen(false)}
            >
              Sign Up
            </Link>
          </div>
        </div>
      </header>

      <main>
        {/* Hero Section */}
        <section id="home" className="landing-hero">
          <div className="landing-container">
            <div className="landing-hero-grid">
              <div>
                <div className="landing-pill">
                  <span>⚡</span>
                  <span>Enterprise AIOps &amp; Cloud Observability Platform</span>
                </div>

                <h1 className="landing-hero-title">
                  Observe Distributed Systems. Diagnose Root Causes Faster.
                </h1>

                <p className="landing-hero-subtitle">
                  Spectra unifies microservice telemetry ingestion, statistical anomaly detection,
                  incident lifecycle tracking, and AI-driven Root Cause Analysis with RAG runbook
                  recommendations in a single operations platform.
                </p>

                <div className="landing-hero-ctas">
                  <Link to="/register" className="landing-btn landing-btn-primary landing-btn-lg">
                    Get Started
                  </Link>
                  <a
                    href="#about"
                    className="landing-btn landing-btn-secondary landing-btn-lg"
                    onClick={(e) => handleNavClick(e, "about")}
                  >
                    Learn More
                  </a>
                </div>

                <div className="landing-hero-meta">
                  <span className="landing-hero-meta-item">
                    <strong>✓</strong> Real-time Z-score &amp; IQR anomaly detection
                  </span>
                  <span className="landing-hero-meta-item">
                    <strong>✓</strong> Automated RCA &amp; RAG runbooks
                  </span>
                  <span className="landing-hero-meta-item">
                    <strong>✓</strong> Role-based enterprise governance
                  </span>
                </div>
              </div>

              {/* Hero Visual Illustration */}
              <div className="hero-visual-card" aria-label="Spectra Observability Console Preview">
                <div className="hero-visual-topbar">
                  <div className="hero-visual-dots">
                    <span className="hero-visual-dot" />
                    <span className="hero-visual-dot" />
                    <span className="hero-visual-dot" />
                  </div>
                  <span style={{ fontWeight: 600 }}>Spectra Telemetry &amp; AI Diagnostics</span>
                  <span className="badge badge-healthy">PIPELINE ACTIVE</span>
                </div>

                <div className="hero-visual-body">
                  <div className="hero-mini-metrics">
                    <div className="hero-mini-stat">
                      <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>Monitored Metrics</div>
                      <div style={{ fontSize: 15, fontWeight: 700, marginTop: 2 }}>CPU · Mem · Latency</div>
                    </div>
                    <div className="hero-mini-stat">
                      <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>Anomaly Engine</div>
                      <div style={{ fontSize: 15, fontWeight: 700, marginTop: 2 }}>Z-Score &amp; IQR</div>
                    </div>
                    <div className="hero-mini-stat">
                      <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>Incident Flow</div>
                      <div style={{ fontSize: 15, fontWeight: 700, marginTop: 2 }}>OPEN → RESOLVED</div>
                    </div>
                  </div>

                  {/* Telemetry Sparkline Visual */}
                  <div
                    style={{
                      backgroundColor: "var(--bg-page)",
                      border: "1px solid var(--border-subtle)",
                      borderRadius: "var(--radius-sm)",
                      padding: 14
                    }}
                  >
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8, fontSize: 12 }}>
                      <span style={{ fontWeight: 600, color: "var(--text-primary)" }}>
                        Service Telemetry Stream (RESPONSE_TIME / CPU_USAGE)
                      </span>
                      <span className="badge badge-warning">|Z| ≥ 3.0 Flagged</span>
                    </div>
                    <svg viewBox="0 0 420 96" style={{ width: "100%", height: "auto", display: "block" }} aria-hidden="true">
                      <line x1="0" y1="24" x2="420" y2="24" stroke="#DCE1E6" strokeDasharray="4 4" />
                      <line x1="0" y1="54" x2="420" y2="54" stroke="#DCE1E6" strokeDasharray="4 4" />
                      <line x1="0" y1="84" x2="420" y2="84" stroke="#DCE1E6" />
                      <path
                        d="M 8 72 L 56 68 L 104 70 L 152 63 L 200 66 L 248 58 L 288 22 L 328 48 L 372 60 L 412 64"
                        fill="none"
                        stroke="#526B86"
                        strokeWidth="3"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                      <circle cx="288" cy="22" r="5.5" fill="#A3312D" stroke="#FFFFFF" strokeWidth="2" />
                    </svg>
                  </div>

                  {/* AI RCA + Runbook Preview */}
                  <div
                    style={{
                      backgroundColor: "var(--accent-light)",
                      border: "1px solid #d5e0eb",
                      borderRadius: "var(--radius-sm)",
                      padding: 12,
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      gap: 12,
                      flexWrap: "wrap"
                    }}
                  >
                    <div>
                      <div style={{ fontSize: 11, fontWeight: 700, color: "var(--accent-hover)", textTransform: "uppercase" }}>
                        AI RCA &amp; RAG Runbook Match
                      </div>
                      <div style={{ fontSize: 13, color: "var(--text-primary)", marginTop: 2, fontWeight: 500 }}>
                        Correlates anomaly signature with remediation steps
                      </div>
                    </div>
                    <span className="badge badge-healthy">Confidence Scored</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* About Spectra */}
        <RevealSection id="about" className="landing-section">
          <div className="landing-container">
            <div className="about-grid">
              <div>
                <span className="section-eyebrow">About Spectra</span>
                <h2 className="section-title">
                  Built to Bring Clarity to Distributed Microservice Operations
                </h2>
                <p style={{ color: "var(--text-secondary)", lineHeight: 1.7, marginBottom: 14, fontSize: 15 }}>
                  <strong>Spectra</strong> is a full-stack Cloud Observability and Automated Operations
                  (AIOps) platform engineered to continuously monitor distributed services, ingest
                  metric telemetry, detect statistical anomalies, and accelerate incident response.
                </p>
                <p style={{ color: "var(--text-secondary)", lineHeight: 1.7, marginBottom: 14, fontSize: 15 }}>
                  <strong>Why it was developed:</strong> Modern microservices generate continuous
                  CPU, memory, latency, and error-rate signals across multiple environments. Relying
                  on fragmented dashboards or manual log triage slows down diagnosis when incidents hit.
                </p>
                <p style={{ color: "var(--text-secondary)", lineHeight: 1.7, fontSize: 15 }}>
                  <strong>The problem it solves:</strong> Spectra connects raw host and service
                  telemetry directly to automated alert rules, anomaly detection, AI Root Cause
                  Analysis, and retrieval-augmented remediation runbooks in one cohesive workflow.
                </p>
              </div>

              <div className="arch-card" aria-label="Spectra System Architecture">
                <div style={{ fontSize: 13, fontWeight: 700, color: "var(--text-primary)", marginBottom: 14 }}>
                  4-Component Platform Architecture
                </div>

                <div className="arch-tier">
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 14 }}>React + Vite Frontend</div>
                    <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                      Dashboards, Service Registry, Telemetry Charts &amp; Incident Boards
                    </div>
                  </div>
                  <span className="badge badge-neutral">React 18</span>
                </div>

                <div className="arch-connector">↓ REST API &amp; RBAC Auth ↓</div>

                <div className="arch-tier">
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 14 }}>Spring Boot Core Backend</div>
                    <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                      Spring Security, Alert Rule Engine &amp; Incident Lifecycle Management
                    </div>
                  </div>
                  <span className="badge badge-neutral">Java 21</span>
                </div>

                <div className="arch-connector">↙ Persistence &nbsp;&nbsp;|&nbsp;&nbsp; AI Inference ↘</div>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
                  <div className="arch-tier" style={{ flexDirection: "column", alignItems: "flex-start" }}>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>PostgreSQL</div>
                    <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>
                      Relational telemetry &amp; audit store
                    </div>
                  </div>
                  <div className="arch-tier" style={{ flexDirection: "column", alignItems: "flex-start" }}>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>FastAPI AI Service</div>
                    <div style={{ fontSize: 11, color: "var(--text-secondary)" }}>
                      Anomaly ML, RCA &amp; RAG Runbooks
                    </div>
                  </div>
                </div>

                <div className="arch-connector">↑ Authenticated Ingestion ↑</div>

                <div className="arch-tier">
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 14 }}>Standalone Java Monitoring Agent</div>
                    <div style={{ fontSize: 12, color: "var(--text-secondary)" }}>
                      Host-level metric collectors &amp; local resilient buffering
                    </div>
                  </div>
                  <span className="badge badge-healthy">Agent</span>
                </div>
              </div>
            </div>
          </div>
        </RevealSection>

        {/* Our Mission and Vision */}
        <RevealSection id="mission-vision" className="landing-section landing-section-alt">
          <div className="landing-container">
            <div className="section-header">
              <span className="section-eyebrow">Our Purpose</span>
              <h2 className="section-title">Mission &amp; Long-Term Vision</h2>
              <p className="section-subtitle">
                Designed for both real-world operational reliability and transparent, measurable AIOps evaluation.
              </p>
            </div>

            <div className="mission-grid">
              <div className="landing-card">
                <div className="icon-box">
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <circle cx="12" cy="12" r="10" />
                    <path d="M12 6v6l4 2" />
                  </svg>
                </div>
                <h3 style={{ fontSize: 18, marginBottom: 10 }}>Our Mission</h3>
                <p style={{ color: "var(--text-secondary)", lineHeight: 1.65, fontSize: 14 }}>
                  Empower engineering and operations teams to continuously observe distributed
                  microservices, surface statistical anomalies before outages escalate, and shorten
                  time-to-resolution with automated Root Cause Analysis and actionable runbook retrieval.
                </p>
              </div>

              <div className="landing-card">
                <div className="icon-box">
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                    <circle cx="12" cy="12" r="3" />
                  </svg>
                </div>
                <h3 style={{ fontSize: 18, marginBottom: 10 }}>Our Vision</h3>
                <p style={{ color: "var(--text-secondary)", lineHeight: 1.65, fontSize: 14 }}>
                  Unify enterprise observability, multi-tenant fleet governance, predictive failure
                  insights, and verifiable model benchmarking into an approachable platform where
                  AI diagnostics remain explainable, auditable, and grounded in real telemetry.
                </p>
              </div>
            </div>
          </div>
        </RevealSection>

        {/* Key Features */}
        <RevealSection id="features" className="landing-section">
          <div className="landing-container">
            <div className="section-header">
              <span className="section-eyebrow">Key Features</span>
              <h2 className="section-title">Everything Needed for End-to-End AIOps</h2>
              <p className="section-subtitle">
                Every capability in Spectra maps directly to your operational workflow—from metric
                collection to incident closure and model evaluation.
              </p>
            </div>

            <div className="features-grid">
              {KEY_FEATURES.map((feature) => (
                <div key={feature.title} className="landing-card">
                  <div className="icon-box">{feature.icon}</div>
                  <h3 style={{ fontSize: 16, marginBottom: 8 }}>{feature.title}</h3>
                  <p style={{ color: "var(--text-secondary)", fontSize: 14, lineHeight: 1.6 }}>
                    {feature.description}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </RevealSection>

        {/* How It Works */}
        <RevealSection id="how-it-works" className="landing-section landing-section-alt">
          <div className="landing-container">
            <div className="section-header">
              <span className="section-eyebrow">How It Works</span>
              <h2 className="section-title">From Raw Telemetry to Guided Resolution</h2>
              <p className="section-subtitle">
                A structured four-step pipeline connects host agents and enterprise integrations to
                automated diagnostics.
              </p>
            </div>

            <div className="steps-grid">
              {HOW_IT_WORKS_STEPS.map((item) => (
                <div key={item.step} className="step-card">
                  <div className="step-number">{item.step}</div>
                  <h3 style={{ fontSize: 16, marginBottom: 8 }}>{item.title}</h3>
                  <p style={{ color: "var(--text-secondary)", fontSize: 14, lineHeight: 1.6 }}>
                    {item.description}
                  </p>
                </div>
              ))}
            </div>
          </div>
        </RevealSection>

        {/* Why Choose Spectra */}
        <RevealSection id="why-spectra" className="landing-section">
          <div className="landing-container">
            <div className="section-header">
              <span className="section-eyebrow">Why Choose Spectra</span>
              <h2 className="section-title">Built for Transparency, Speed, and Reliability</h2>
              <p className="section-subtitle">
                Thoughtful engineering choices across the backend, AI engine, and operator UI set
                Spectra apart.
              </p>
            </div>

            <div className="why-grid">
              {WHY_SPECTRA_ITEMS.map((item) => (
                <div key={item.title} className="why-item">
                  <div className="icon-box" style={{ flexShrink: 0, marginBottom: 0 }}>
                    {item.icon}
                  </div>
                  <div>
                    <h3 style={{ fontSize: 16, marginBottom: 6 }}>{item.title}</h3>
                    <p style={{ color: "var(--text-secondary)", fontSize: 14, lineHeight: 1.6 }}>
                      {item.description}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </RevealSection>

        {/* Call to Action */}
        <RevealSection id="get-started" className="landing-section" style={{ paddingTop: 24 }}>
          <div className="landing-container">
            <div className="cta-banner">
              <h2>Ready to Monitor Your Stack with Spectra?</h2>
              <p>
                Create an operator account to register services, ingest telemetry, inspect anomalies,
                and run AI Root Cause Analysis—or sign in to your existing workspace.
              </p>
              <div style={{ display: "flex", justifyContent: "center", gap: 12, flexWrap: "wrap" }}>
                <Link
                  to="/register"
                  className="landing-btn landing-btn-lg"
                  style={{ backgroundColor: "#FFFFFF", color: "#273444" }}
                >
                  Get Started Free
                </Link>
                <Link
                  to="/login"
                  className="landing-btn landing-btn-lg"
                  style={{
                    backgroundColor: "transparent",
                    color: "#FFFFFF",
                    borderColor: "rgba(255, 255, 255, 0.4)"
                  }}
                >
                  Sign In
                </Link>
              </div>
            </div>
          </div>
        </RevealSection>

        {/* Contact Section */}
        <RevealSection id="contact" className="landing-section landing-section-alt">
          <div className="landing-container">
            <div className="contact-grid">
              <div>
                <span className="section-eyebrow">Contact &amp; Project Links</span>
                <h2 className="section-title">Connect with the Spectra Project</h2>
                <p style={{ color: "var(--text-secondary)", lineHeight: 1.65, fontSize: 15, marginBottom: 20 }}>
                  Explore the open-source architecture, deploy locally with Docker Compose, or leave
                  a message about onboarding an enterprise workspace.
                </p>

                <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
                  <div className="landing-card" style={{ padding: 16 }}>
                    <div style={{ fontSize: 12, fontWeight: 700, color: "var(--text-secondary)", textTransform: "uppercase" }}>
                      Source Repository
                    </div>
                    <a
                      href="https://github.com/siddharthagummadi/spectra"
                      target="_blank"
                      rel="noreferrer"
                      style={{ fontSize: 14, fontWeight: 600, display: "inline-block", marginTop: 4 }}
                    >
                      github.com/siddharthagummadi/spectra →
                    </a>
                  </div>

                  <div className="landing-card" style={{ padding: 16 }}>
                    <div style={{ fontSize: 12, fontWeight: 700, color: "var(--text-secondary)", textTransform: "uppercase" }}>
                      Supported Deployments
                    </div>
                    <div style={{ fontSize: 13, color: "var(--text-primary)", marginTop: 4 }}>
                      Local Stack · Docker Compose + Prometheus · Render + Vercel
                    </div>
                  </div>
                </div>
              </div>

              <div className="landing-card">
                <h3 style={{ fontSize: 17, marginBottom: 6 }}>Send an Inquiry</h3>
                <p style={{ fontSize: 13, color: "var(--text-secondary)", marginBottom: 16 }}>
                  Have questions about Spectra or enterprise onboarding? Drop a note below.
                </p>

                {contactSent && (
                  <div className="alert-banner alert-banner-success" role="status">
                    <span>Thank you! Your message has been recorded. You can also sign up directly to explore the platform.</span>
                    <button
                      type="button"
                      className="alert-banner-close"
                      aria-label="Dismiss message"
                      onClick={() => setContactSent(false)}
                    >
                      &times;
                    </button>
                  </div>
                )}

                <form onSubmit={handleContactSubmit}>
                  <div className="form-group">
                    <label className="form-label" htmlFor="contact-name">Your Name</label>
                    <input
                      id="contact-name"
                      type="text"
                      className="form-input"
                      placeholder="e.g. Alex Rivera"
                      value={contactName}
                      onChange={(e) => setContactName(e.target.value)}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label" htmlFor="contact-email">Email Address</label>
                    <input
                      id="contact-email"
                      type="email"
                      className="form-input"
                      placeholder="you@organization.com"
                      value={contactEmail}
                      onChange={(e) => setContactEmail(e.target.value)}
                      required
                    />
                  </div>
                  <div className="form-group">
                    <label className="form-label" htmlFor="contact-message">Message</label>
                    <textarea
                      id="contact-message"
                      rows={3}
                      className="form-textarea"
                      placeholder="Tell us about your services, telemetry stack, or question..."
                      value={contactMessage}
                      onChange={(e) => setContactMessage(e.target.value)}
                      required
                    />
                  </div>
                  <button type="submit" className="landing-btn landing-btn-primary" style={{ width: "100%" }}>
                    Send Message
                  </button>
                </form>
              </div>
            </div>
          </div>
        </RevealSection>
      </main>

      {/* Footer */}
      <footer className="landing-footer">
        <div className="landing-container">
          <div className="footer-grid">
            <div>
              <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 12 }}>
                <SpectraLogoMark size={26} />
                <span style={{ fontSize: 16, fontWeight: 700, color: "#FFFFFF", letterSpacing: "0.04em" }}>
                  SPECTRA
                </span>
              </div>
              <p style={{ fontSize: 13, lineHeight: 1.65, color: "#94a3b8", maxWidth: 300 }}>
                Enterprise AIOps &amp; Cloud Observability platform for microservice monitoring,
                anomaly detection, incident management, and AI-driven Root Cause Analysis.
              </p>
            </div>

            <div>
              <div className="footer-heading">Navigation</div>
              <ul className="footer-links">
                {NAV_LINKS.map((item) => (
                  <li key={item.id}>
                    <a
                      href={`#${item.id}`}
                      className="footer-link"
                      onClick={(e) => handleNavClick(e, item.id)}
                    >
                      {item.label}
                    </a>
                  </li>
                ))}
              </ul>
            </div>

            <div>
              <div className="footer-heading">Platform Access</div>
              <ul className="footer-links">
                <li><Link to="/login" className="footer-link">Sign In</Link></li>
                <li><Link to="/register" className="footer-link">Create Account</Link></li>
                <li><a href="#why-spectra" className="footer-link" onClick={(e) => handleNavClick(e, "why-spectra")}>Why Spectra</a></li>
                <li><a href="#mission-vision" className="footer-link" onClick={(e) => handleNavClick(e, "mission-vision")}>Mission &amp; Vision</a></li>
              </ul>
            </div>

            <div>
              <div className="footer-heading">Project &amp; Social</div>
              <ul className="footer-links">
                <li>
                  <a
                    href="https://github.com/siddharthagummadi/spectra"
                    target="_blank"
                    rel="noreferrer"
                    className="footer-link"
                    style={{ display: "inline-flex", alignItems: "center", gap: 6 }}
                  >
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                      <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0 0 24 12c0-6.63-5.37-12-12-12z" />
                    </svg>
                    GitHub Repository
                  </a>
                </li>
                <li>
                  <a href="#contact" className="footer-link" onClick={(e) => handleNavClick(e, "contact")}>
                    Contact Form
                  </a>
                </li>
              </ul>
            </div>
          </div>

          <div className="footer-bottom">
            <span>© {new Date().getFullYear()} Spectra — Enterprise AIOps &amp; Observability Platform.</span>
            <span>Developed for enterprise observability and academic evaluation.</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
