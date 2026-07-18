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
   * WebSocket URL of the RFC-005 realtime endpoint.
   *
   * The transport contract (RFC-009): the client is a native WebSocket
   * (@stomp/stompjs brokerURL, no sockjs-client), and the backend registers
   * the SockJS endpoint `/ws` — whose raw-WebSocket transport lives at
   * `/ws/websocket`. Every resolution below lands on that raw transport.
   *
   * Resolution order: explicit VITE_WS_URL, normalized (http(s)→ws(s), and
   * the `/websocket` transport suffix appended when the URL names the
   * endpoint root — so `https://api.example.com/ws` and
   * `wss://api.example.com/ws/websocket` configure the same thing);
   * otherwise derived from VITE_API_BASE_URL — the realtime endpoint lives
   * on the backend, so in the split-domain production topology (RFC-008)
   * the static frontend host would 404 the handshake; same-origin only when
   * neither is configured (local dev behind the Vite proxy).
   */
  wsUrl(): string {
    const configured = import.meta.env.VITE_WS_URL as string | undefined;
    if (configured) {
      return toRawTransportUrl(configured);
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

/**
 * Normalizes an operator-supplied realtime URL to the native transport the
 * client actually opens: ws/wss scheme (a browser WebSocket accepts nothing
 * else), targeting the SockJS raw-WebSocket transport.
 */
function toRawTransportUrl(configured: string): string {
  const url = new URL(configured);
  if (url.protocol === "https:") {
    url.protocol = "wss:";
  } else if (url.protocol === "http:") {
    url.protocol = "ws:";
  }
  const path = url.pathname.replace(/\/+$/, "");
  url.pathname = path.endsWith("/websocket") ? path : `${path}/websocket`;
  return url.toString();
}
