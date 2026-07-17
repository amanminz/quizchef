/// <reference types="vitest/config" />
import path from "node:path";
import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const directoryName = path.dirname(fileURLToPath(import.meta.url));

// The dev server proxies API and WebSocket traffic to the local backend so
// the frontend runs same-origin in development — no CORS on either side,
// matching production behind a reverse proxy (RFC-008).
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(directoryName, "./src")
    }
  },
  server: {
    port: 5173,
    proxy: {
      "/api": "http://localhost:8080",
      "/ws": { target: "http://localhost:8080", ws: true }
    }
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["src/test/setup.ts"],
    css: false
  }
});
