import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router-dom";
import { AppLayout } from "../AppLayout";
import { AuthProvider, useAuth } from "../../../context/AuthContext";
import * as usersApi from "../../../api/usersApi";
import * as aiApi from "../../../api/aiApi";
import * as notificationsApi from "../../../api/notificationsApi";

function AuthSeeder({ children }) {
  const { login, isAuthenticated } = useAuth();
  const [ready, setReady] = React.useState(false);

  React.useEffect(() => {
    login({ email: "operator@spectra.local", role: "USER", enterpriseName: "Acme Corp" }, "dGVzdC1jcmVkcw==");
    setReady(true);
  }, [login]);

  if (!ready || !isAuthenticated) return null;
  return children;
}

function renderLayout(initialRoute = "/dashboard") {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[initialRoute]}>
        <AuthSeeder>
          <Routes>
            <Route path="/" element={<AppLayout />}>
              <Route path="dashboard" element={<div>Dashboard Content</div>} />
              <Route path="services" element={<div>Services Content</div>} />
            </Route>
          </Routes>
        </AuthSeeder>
      </MemoryRouter>
    </AuthProvider>
  );
}

describe("AppLayout responsive navigation drawer", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(usersApi, "checkBackendHealth").mockResolvedValue({ status: "UP" });
    vi.spyOn(aiApi, "getAiHealth").mockResolvedValue({ status: "UP" });
    vi.spyOn(notificationsApi, "getUnreadCount").mockResolvedValue({ count: 0 });
    vi.spyOn(notificationsApi, "getNotifications").mockResolvedValue([]);
  });

  it("toggles the mobile sidebar drawer via menu and close buttons", async () => {
    renderLayout();

    const menuBtn = await screen.findByLabelText("Toggle navigation menu");
    const sidebar = screen.getByLabelText("Primary navigation");

    expect(menuBtn).toHaveAttribute("aria-expanded", "false");
    expect(sidebar).not.toHaveClass("is-open");

    fireEvent.click(menuBtn);
    expect(menuBtn).toHaveAttribute("aria-expanded", "true");
    expect(sidebar).toHaveClass("is-open");

    const closeBtn = screen.getByLabelText("Close navigation menu");
    fireEvent.click(closeBtn);
    expect(menuBtn).toHaveAttribute("aria-expanded", "false");
    expect(sidebar).not.toHaveClass("is-open");
  });

  it("closes the drawer on Escape key and on route navigation", async () => {
    renderLayout();

    const menuBtn = await screen.findByLabelText("Toggle navigation menu");
    const sidebar = screen.getByLabelText("Primary navigation");

    fireEvent.click(menuBtn);
    expect(sidebar).toHaveClass("is-open");

    fireEvent.keyDown(window, { key: "Escape" });
    expect(sidebar).not.toHaveClass("is-open");

    fireEvent.click(menuBtn);
    expect(sidebar).toHaveClass("is-open");

    fireEvent.click(screen.getByText("Services"));
    await waitFor(() => {
      expect(screen.getByText("Services Content")).toBeInTheDocument();
      expect(sidebar).not.toHaveClass("is-open");
    });
  });
});
