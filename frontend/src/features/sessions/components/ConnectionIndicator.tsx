import type { RealtimeConnectionState } from "@/realtime/RealtimeClient";
import { cn } from "@/utils/cn";

const labels: Record<RealtimeConnectionState, string> = {
  disconnected: "Offline",
  connecting: "Connecting…",
  connected: "Live",
  reconnecting: "Reconnecting…"
};

const dotClasses: Record<RealtimeConnectionState, string> = {
  disconnected: "bg-muted-foreground",
  connecting: "bg-primary animate-pulse",
  connected: "bg-success",
  reconnecting: "bg-destructive animate-pulse"
};

/** The realtime channel's state, as a dot + label (reads connectionStore upstream). */
export function ConnectionIndicator({ status }: { status: RealtimeConnectionState }) {
  return (
    <span className="inline-flex items-center gap-2 text-sm text-muted-foreground">
      <span aria-hidden className={cn("h-2 w-2 rounded-full", dotClasses[status])} />
      {labels[status]}
    </span>
  );
}
