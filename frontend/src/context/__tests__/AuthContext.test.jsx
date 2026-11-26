import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, act } from "@testing-library/react";
import { AuthProvider, useAuth } from "../AuthContext";

function TestConsumer() {
  const { user, credentials, isAuthenticated, login, logout } = useAuth();
  return (
    <div>
      <div data-testid="auth-status">{isAuthenticated ? "authenticated" : "anonymous"}</div>
      <div data-testid="user-email">{user?.email || "none"}</div>
      <div data-testid="credentials">{credentials || "none"}</div>
      <button onClick={() => login("dev@example.com", "bW9jay1jcmVkcw==")}>Login</button>
      <button onClick={logout}>Logout</button>
    </div>
  );
}

describe("AuthContext", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
  });

  it("persists credentials and user session to survive browser refreshes (Phase 10)", () => {
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    );

    expect(screen.getByTestId("auth-status").textContent).toBe("anonymous");
    expect(screen.getByTestId("user-email").textContent).toBe("none");
    expect(screen.getByTestId("credentials").textContent).toBe("none");

    act(() => {
      screen.getByText("Login").click();
    });

    expect(screen.getByTestId("auth-status").textContent).toBe("authenticated");
    expect(screen.getByTestId("user-email").textContent).toBe("dev@example.com");
    expect(screen.getByTestId("credentials").textContent).toBe("bW9jay1jcmVkcw==");

    // Verify session persistence in storage
    expect(localStorage.getItem("spectra_auth_credentials")).toBe("bW9jay1jcmVkcw==");
    expect(JSON.parse(localStorage.getItem("spectra_auth_user")).email).toBe("dev@example.com");

    act(() => {
      screen.getByText("Logout").click();
    });

    expect(screen.getByTestId("auth-status").textContent).toBe("anonymous");
    expect(screen.getByTestId("user-email").textContent).toBe("none");
    expect(localStorage.getItem("spectra_auth_credentials")).toBeNull();
  });

  it("synchronously restores authenticated session from storage on page load/refresh", () => {
    localStorage.setItem("spectra_auth_credentials", "stored-refresh-creds");
    localStorage.setItem("spectra_auth_user", JSON.stringify({ email: "restored@spectra.local", role: "ADMIN" }));

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    );

    expect(screen.getByTestId("auth-status").textContent).toBe("authenticated");
    expect(screen.getByTestId("user-email").textContent).toBe("restored@spectra.local");
    expect(screen.getByTestId("credentials").textContent).toBe("stored-refresh-creds");
  });
});
