import { AlertTriangle, CheckCircle2 } from "lucide-react";
import type { RealtimeConnectionState } from "@/realtime/RealtimeClient";
import { cn } from "@/utils/cn";

export interface ReadinessPanelProps {
  connectionStatus: RealtimeConnectionState;
  quizTitle: string | undefined;
  questionCount: number | undefined;
  playerCount: number;
}

/**
 * The host's pre-flight summary: one glance before starting a live event.
 * Purely a projection of state the lobby already holds — no new
 * orchestration, and only actionable problems are highlighted.
 */
export function ReadinessPanel({
  connectionStatus,
  quizTitle,
  questionCount,
  playerCount
}: ReadinessPanelProps) {
  const rows: { ok: boolean; label: string }[] = [
    {
      ok: connectionStatus === "connected",
      label: connectionStatus === "connected" ? "Realtime connected" : "Realtime disconnected"
    },
    {
      ok: quizTitle !== undefined,
      label: quizTitle !== undefined ? "Quiz ready" : "Quiz still loading"
    },
    {
      ok: (questionCount ?? 0) > 0,
      label:
        questionCount !== undefined
          ? `${questionCount} question${questionCount === 1 ? "" : "s"}`
          : "Counting questions…"
    },
    {
      ok: playerCount > 0,
      label: `${playerCount} player${playerCount === 1 ? "" : "s"} joined`
    }
  ];

  return (
    <div className="rounded-lg border bg-card px-4 py-3">
      <ul className="flex flex-col gap-1.5">
        {rows.map((row) => (
          <li
            key={row.label}
            className={cn(
              "flex items-center gap-2 text-sm",
              row.ok ? "text-muted-foreground" : "font-medium text-destructive"
            )}
          >
            {row.ok ? (
              <CheckCircle2 aria-hidden className="h-4 w-4 text-success" />
            ) : (
              <AlertTriangle aria-hidden className="h-4 w-4" />
            )}
            {row.label}
          </li>
        ))}
      </ul>
    </div>
  );
}
