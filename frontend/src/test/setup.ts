import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterAll, afterEach, beforeAll } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { useConnectionStore } from "@/realtime/connectionStore";
import { server } from "@/test/server";
import { useUiPreferencesStore } from "@/theme/uiPreferencesStore";

// jsdom has no matchMedia; the theme hook needs a minimal stand-in.
if (!window.matchMedia) {
  window.matchMedia = (query: string): MediaQueryList =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      addListener: () => undefined,
      removeListener: () => undefined,
      dispatchEvent: () => false
    }) as MediaQueryList;
}

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));

afterEach(() => {
  server.resetHandlers();
  cleanup();
  localStorage.clear();
  useAuthStore.setState({ token: null, sessionExpired: false });
  useConnectionStore.setState({ status: "disconnected" });
  useUiPreferencesStore.setState({ theme: "system" });
});

afterAll(() => server.close());
