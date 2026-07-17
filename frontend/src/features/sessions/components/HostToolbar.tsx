import { Play } from "lucide-react";
import type { ReactNode } from "react";
import { Button } from "@/components/common/Button";
import { ConnectionIndicator } from "@/features/sessions/components/ConnectionIndicator";
import type { RealtimeConnectionState } from "@/realtime/RealtimeClient";

export interface HostToolbarProps {
  connectionStatus: RealtimeConnectionState;
  /** Server-derived: in LOBBY with at least one participant. */
  canStart: boolean;
  /** Shown as the Start button's tooltip while it is disabled. */
  startDisabledReason?: string;
  onStart: () => void;
  isStarting: boolean;
  /** Extra host actions (back links, secondary commands). */
  children?: ReactNode;
}

/**
 * The host's control strip at the top of the lobby: connection state on
 * one side, the Start command on the other. Start stays disabled until the
 * server-reported state says the session is startable and is never
 * optimistic — the page transitions on the confirmed response (or the
 * session.started event), not on the click.
 */
export function HostToolbar({
  connectionStatus,
  canStart,
  startDisabledReason,
  onStart,
  isStarting,
  children
}: HostToolbarProps) {
  return (
    <div className="mb-6 flex flex-wrap items-center justify-between gap-3 rounded-lg border bg-card px-4 py-3">
      <ConnectionIndicator status={connectionStatus} />
      <div className="flex items-center gap-2">
        {children}
        <Button
          onClick={onStart}
          disabled={!canStart}
          isLoading={isStarting}
          title={canStart ? undefined : startDisabledReason}
        >
          <Play aria-hidden className="h-4 w-4" />
          Start session
        </Button>
      </div>
    </div>
  );
}
