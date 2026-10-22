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

  it("manages credentials exclusively in React memory without persistent storage", () => {
    const localSetSpy = vi.spyOn(Storage.prototype, "setItem");

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

    expect(localStorage.getItem("spectra_auth_credentials")).toBeNull();
    expect(localStorage.getItem("spectra_auth_user")).toBeNull();
    expect(sessionStorage.getItem("spectra_auth_credentials")).toBeNull();
    expect(sessionStorage.getItem("spectra_auth_user")).toBeNull();

    expect(localSetSpy).not.toHaveBeenCalledWith("spectra_auth_credentials", expect.anything());

    act(() => {
      screen.getByText("Logout").click();
    });

    expect(screen.getByTestId("auth-status").textContent).toBe("anonymous");
    expect(screen.getByTestId("user-email").textContent).toBe("none");

    localSetSpy.mockRestore();
  });

  it("clears any legacy credentials from storage on initialization", () => {
    localStorage.setItem("spectra_auth_credentials", "legacy-creds");
    localStorage.setItem("spectra_auth_user", JSON.stringify({ email: "legacy@spectra.local" }));
    sessionStorage.setItem("spectra_auth_credentials", "legacy-session-creds");
    sessionStorage.setItem("spectra_auth_user", JSON.stringify({ email: "legacy@spectra.local" }));

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    );

    expect(localStorage.getItem("spectra_auth_credentials")).toBeNull();
    expect(localStorage.getItem("spectra_auth_user")).toBeNull();
    expect(sessionStorage.getItem("spectra_auth_credentials")).toBeNull();
    expect(sessionStorage.getItem("spectra_auth_user")).toBeNull();

    expect(screen.getByTestId("auth-status").textContent).toBe("anonymous");
  });
});
