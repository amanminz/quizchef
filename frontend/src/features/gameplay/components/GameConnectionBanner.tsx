import { useEffect, useRef, useState } from "react";
import type { RealtimeConnectionState } from "@/realtime/RealtimeClient";

/**
 * A reconnect banner for gameplay screens — the same "connection lost,
 * the game itself is unaffected" message the lobby shows (PR #3), named
 * and owned by this feature since gameplay is its own module. When the
 * connection comes back, a brief "Connection restored." confirms recovery
 * so the host never wonders whether a refresh is needed.
 */
export function GameConnectionBanner({ status }: { status: RealtimeConnectionState }) {
  const [restored, setRestored] = useState(false);
  const previousStatus = useRef(status);

  useEffect(() => {
    const wasDown =
      previousStatus.current === "reconnecting" || previousStatus.current === "disconnected";
    previousStatus.current = status;
    if (wasDown && status === "connected") {
      setRestored(true);
      const timer = setTimeout(() => setRestored(false), 5_000);
      return () => clearTimeout(timer);
    }
  }, [status]);

  if (status === "reconnecting" || status === "disconnected") {
    return (
      <div
        role="alert"
        className="mb-4 rounded-md border border-destructive/40 bg-destructive/5 px-4 py-3 text-sm"
      >
        Realtime connection lost — updates are paused while we reconnect. The game itself is
        unaffected.
      </div>
    );
  }

  if (restored) {
    return (
      <div
        role="status"
        className="mb-4 rounded-md border border-success/40 bg-success/5 px-4 py-3 text-sm"
      >
        Connection restored.
      </div>
    );
  }

  return null;
}
