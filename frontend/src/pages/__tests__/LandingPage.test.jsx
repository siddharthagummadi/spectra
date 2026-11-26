import React from "react";
import { describe, it, expect, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { AuthProvider } from "../../context/AuthContext";
import { App } from "../../App";
import { LandingPage } from "../LandingPage";

describe("LandingPage and public routing", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it("renders all required sections and grounded Spectra content", () => {
    render(
      <MemoryRouter>
        <LandingPage />
      </MemoryRouter>
    );

    expect(screen.getByText(/Observe Distributed Systems\. Diagnose Root Causes Faster\./i)).toBeInTheDocument();
    expect(screen.getByText("About Spectra")).toBeInTheDocument();
    expect(screen.getByText(/Mission & Long-Term Vision/i)).toBeInTheDocument();
    expect(screen.getByText(/Everything Needed for End-to-End AIOps/i)).toBeInTheDocument();
    expect(screen.getByText(/From Raw Telemetry to Guided Resolution/i)).toBeInTheDocument();
    expect(screen.getByText(/Built for Transparency, Speed, and Reliability/i)).toBeInTheDocument();
    expect(screen.getByText(/Ready to Monitor Your Stack with Spectra\?/i)).toBeInTheDocument();
    expect(screen.getByText(/Connect with the Spectra Project/i)).toBeInTheDocument();

    expect(screen.getAllByRole("link", { name: /Sign In/i })[0]).toHaveAttribute("href", "/login");
    expect(screen.getAllByRole("link", { name: /Sign Up/i })[0]).toHaveAttribute("href", "/register");
    expect(screen.getByRole("link", { name: /Get Started$/i })).toHaveAttribute("href", "/register");
  });

  it("toggles mobile nav drawer and submits contact inquiry form", () => {
    render(
      <MemoryRouter>
        <LandingPage />
      </MemoryRouter>
    );

    const toggleBtn = screen.getByLabelText("Toggle landing navigation menu");
    expect(toggleBtn).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(toggleBtn);
    expect(toggleBtn).toHaveAttribute("aria-expanded", "true");

    fireEvent.click(screen.getByRole("tab", { name: "Anomaly |Z|" }));
    expect(screen.getByText("Z-Score Spike Caught")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("tab", { name: "AI RCA & Runbooks" }));
    expect(screen.getByText(/Correlates telemetry breaches into primary root cause/i)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Your Name"), { target: { value: "Jane Doe" } });
    fireEvent.change(screen.getByLabelText("Email Address"), { target: { value: "jane@acme.test" } });
    fireEvent.change(screen.getByLabelText("Message"), { target: { value: "Interested in Spectra AIOps." } });
    fireEvent.click(screen.getByRole("button", { name: "Send Message" }));

    expect(screen.getByRole("status")).toHaveTextContent(/Thank you! Your message has been recorded/i);
  });

  it("shows LandingPage at / and navigates to Sign Up registration tab when clicked", () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/"]}>
          <App />
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByText(/Observe Distributed Systems\. Diagnose Root Causes Faster\./i)).toBeInTheDocument();

    fireEvent.click(screen.getAllByRole("link", { name: /Sign Up/i })[0]);
    expect(screen.getByLabelText("Full Name")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create Account" })).toBeInTheDocument();
  });

  it("redirects unauthenticated protected routes like /dashboard to /login", () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={["/dashboard"]}>
          <App />
        </MemoryRouter>
      </AuthProvider>
    );

    expect(screen.getByRole("button", { name: "Sign In to Platform" })).toBeInTheDocument();
  });
});
