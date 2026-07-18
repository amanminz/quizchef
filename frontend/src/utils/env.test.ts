import { afterEach, describe, expect, it, vi } from "vitest";
import { env } from "@/utils/env";

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("env.wsUrl", () => {
  it("uses an explicit VITE_WS_URL that already names the raw transport", () => {
    vi.stubEnv("VITE_WS_URL", "wss://realtime.example.com/ws/websocket");
    vi.stubEnv("VITE_API_BASE_URL", "https://api.example.com");

    expect(env.wsUrl()).toBe("wss://realtime.example.com/ws/websocket");
  });

  it("appends the SockJS raw-transport suffix when VITE_WS_URL names the endpoint root", () => {
    vi.stubEnv("VITE_WS_URL", "wss://api.example.com/ws");

    expect(env.wsUrl()).toBe("wss://api.example.com/ws/websocket");
  });

  it("normalizes an https VITE_WS_URL to wss — a browser WebSocket accepts nothing else", () => {
    vi.stubEnv("VITE_WS_URL", "https://api.example.com/ws");

    expect(env.wsUrl()).toBe("wss://api.example.com/ws/websocket");
  });

  it("normalizes an http VITE_WS_URL to plain ws", () => {
    vi.stubEnv("VITE_WS_URL", "http://localhost:8080/ws");

    expect(env.wsUrl()).toBe("ws://localhost:8080/ws/websocket");
  });

  it("tolerates a trailing slash without doubling separators", () => {
    vi.stubEnv("VITE_WS_URL", "wss://api.example.com/ws/");

    expect(env.wsUrl()).toBe("wss://api.example.com/ws/websocket");
  });

  it("derives the realtime endpoint from the API origin when only VITE_API_BASE_URL is set", () => {
    // The split-domain production topology (RFC-008): the frontend host
    // serves static files only, so same-origin would 404 the handshake.
    vi.stubEnv("VITE_API_BASE_URL", "https://api.example.com");

    expect(env.wsUrl()).toBe("wss://api.example.com/ws/websocket");
  });

  it("resolves the production Railway variables to the deployed raw transport", () => {
    vi.stubEnv("VITE_API_BASE_URL", "https://quizchef-backend-production.up.railway.app");

    expect(env.wsUrl()).toBe("wss://quizchef-backend-production.up.railway.app/ws/websocket");
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
