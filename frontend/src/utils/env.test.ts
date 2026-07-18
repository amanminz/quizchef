import { afterEach, describe, expect, it, vi } from "vitest";
import { env } from "@/utils/env";

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("env.wsUrl", () => {
  it("prefers an explicit VITE_WS_URL over everything", () => {
    vi.stubEnv("VITE_WS_URL", "wss://realtime.example.com/ws/websocket");
    vi.stubEnv("VITE_API_BASE_URL", "https://api.example.com");

    expect(env.wsUrl()).toBe("wss://realtime.example.com/ws/websocket");
  });

  it("derives the realtime endpoint from the API origin when only VITE_API_BASE_URL is set", () => {
    // The split-domain production topology (RFC-008): the frontend host
    // serves static files only, so same-origin would 404 the handshake.
    vi.stubEnv("VITE_API_BASE_URL", "https://api.example.com");

    expect(env.wsUrl()).toBe("wss://api.example.com/ws/websocket");
  });

  it("keeps plain ws for an http API origin", () => {
    vi.stubEnv("VITE_API_BASE_URL", "http://localhost:8080");

    expect(env.wsUrl()).toBe("ws://localhost:8080/ws/websocket");
  });

  it("falls back to same-origin when nothing is configured", () => {
    const scheme = window.location.protocol === "https:" ? "wss" : "ws";

    expect(env.wsUrl()).toBe(`${scheme}://${window.location.host}/ws/websocket`);
  });
});
