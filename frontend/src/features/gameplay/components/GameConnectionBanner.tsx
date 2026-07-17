import type { RealtimeConnectionState } from "@/realtime/RealtimeClient";

/**
 * A reconnect banner for gameplay screens — the same "connection lost,
 * the game itself is unaffected" message the lobby shows (PR #3), named
 * and owned by this feature since gameplay is its own module.
 */
export function GameConnectionBanner({ status }: { status: RealtimeConnectionState }) {
  if (status !== "reconnecting" && status !== "disconnected") {
    return null;
  }
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
