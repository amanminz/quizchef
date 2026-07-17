import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterAll, afterEach, beforeAll } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { usePlayerSessionStore } from "@/features/gameplay/playerSessionStore";
import { useHostedSessionsStore } from "@/features/sessions/hostedSessionsStore";
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

// jsdom doesn't implement the <dialog> element's imperative API - Modal
// (and anything built on it, like ConfirmDialog) needs showModal/close to
// exist and toggle the `open` attribute the way a real browser would.
if (!HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
    this.setAttribute("open", "");
  };
  HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
    this.removeAttribute("open");
    this.dispatchEvent(new Event("close"));
  };
}

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));

afterEach(() => {
  server.resetHandlers();
  cleanup();
  localStorage.clear();
  useAuthStore.setState({ token: null, sessionExpired: false });
  useConnectionStore.setState({ status: "disconnected" });
  useHostedSessionsStore.setState({ sessionIds: [] });
  usePlayerSessionStore.setState({ bySessionPin: {} });
  useUiPreferencesStore.setState({ theme: "system" });
});

afterAll(() => server.close());
