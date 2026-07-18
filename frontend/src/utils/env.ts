/**
 * Typed access to build-time configuration. Every value has a same-origin
 * default so a plain `npm run dev` needs no .env file (the Vite proxy routes
 * /api and /ws to the local backend). Values are read lazily so tests can
 * stub them.
 */
export const env = {
  /** Base URL for REST calls; empty means same-origin. */
  get apiBaseUrl(): string {
    return (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "";
  },

  /**
   * WebSocket URL of the RFC-005 realtime endpoint. Resolution order:
   * explicit VITE_WS_URL; otherwise derived from VITE_API_BASE_URL — the
   * realtime endpoint lives on the backend, so in the split-domain
   * production topology (RFC-008) the frontend host would 404 the
   * handshake; same-origin only when neither is configured (local dev
   * behind the Vite proxy).
   */
  wsUrl(): string {
    const configured = import.meta.env.VITE_WS_URL as string | undefined;
    if (configured) {
      return configured;
    }
    if (this.apiBaseUrl) {
      const api = new URL(this.apiBaseUrl, window.location.href);
      const scheme = api.protocol === "https:" ? "wss" : "ws";
      return `${scheme}://${api.host}/ws/websocket`;
    }
    const scheme = window.location.protocol === "https:" ? "wss" : "ws";
    return `${scheme}://${window.location.host}/ws/websocket`;
  }
};
