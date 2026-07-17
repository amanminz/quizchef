/**
 * Typed access to build-time configuration. Every value has a same-origin
 * default so a plain `npm run dev` needs no .env file (the Vite proxy routes
 * /api and /ws to the local backend).
 */
export const env = {
  /** Base URL for REST calls; empty means same-origin. */
  apiBaseUrl: (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? "",

  /** WebSocket URL of the RFC-005 realtime endpoint. */
  wsUrl(): string {
    const configured = import.meta.env.VITE_WS_URL as string | undefined;
    if (configured) {
      return configured;
    }
    const scheme = window.location.protocol === "https:" ? "wss" : "ws";
    return `${scheme}://${window.location.host}/ws/websocket`;
  }
};
