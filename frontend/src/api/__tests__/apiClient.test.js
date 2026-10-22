import { describe, it, expect, beforeEach, vi } from "vitest";
import { request, setApiCredentials, setUnauthorizedHandler } from "../apiClient";

describe("apiClient", () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    setApiCredentials(null);
    setUnauthorizedHandler(null);
  });

  it("adds Basic Authorization header when credentials exist", async () => {
    setApiCredentials("dGVzdDpwb3J0");
    const mockFetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ "content-type": "application/json" }),
      json: async () => ({ success: true })
    });
    global.fetch = mockFetch;

    const data = await request("/api/services");

    expect(data).toEqual({ success: true });
    expect(mockFetch).toHaveBeenCalledWith(
      "http://localhost:8080/api/services",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Basic dGVzdDpwb3J0",
          "Content-Type": "application/json"
        })
      })
    );
  });

  it("returns null on 204 No Content", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      headers: new Headers()
    });

    const result = await request("/api/test");
    expect(result).toBeNull();
  });

  it("extracts error message, status, and fieldErrors on failure", async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      headers: new Headers({ "content-type": "application/json" }),
      json: async () => ({
        status: 400,
        error: "Bad Request",
        message: "Validation Failed",
        fieldErrors: { name: "Name is required" }
      })
    });

    try {
      await request("/api/services", { method: "POST" });
      expect.unreachable();
    } catch (err) {
      expect(err.status).toBe(400);
      expect(err.message).toBe("Validation Failed");
      expect(err.fieldErrors).toEqual({ name: "Name is required" });
    }
  });

  it("calls unauthorized handler when response is 401", async () => {
    const unauthorizedSpy = vi.fn();
    setUnauthorizedHandler(unauthorizedSpy);

    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      headers: new Headers({ "content-type": "application/json" }),
      json: async () => ({ message: "Unauthorized" })
    });

    await expect(request("/api/protected")).rejects.toThrow("Unauthorized");
    expect(unauthorizedSpy).toHaveBeenCalledTimes(1);
  });
});
