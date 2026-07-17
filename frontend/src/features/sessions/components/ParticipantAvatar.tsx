import { cn } from "@/utils/cn";

/**
 * A deterministic avatar for a participant known only by id — the roster
 * messages carry no display name (RFC-005 keeps roster churn cheap), so
 * the avatar derives a stable hue and a two-character tag from the id.
 * Disconnected participants dim rather than disappear (ADR-003: durable
 * participants).
 */
export function ParticipantAvatar({
  participantId,
  status
}: {
  participantId: string;
  status: "connected" | "disconnected";
}) {
  let hash = 0;
  for (const char of participantId) {
    hash = (hash * 31 + char.charCodeAt(0)) % 360;
  }

  return (
    <span
      aria-hidden
      className={cn(
        "inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-semibold uppercase text-white",
        status === "disconnected" && "opacity-40 grayscale"
      )}
      style={{ backgroundColor: `hsl(${hash} 60% 45%)` }}
    >
      {participantId.slice(0, 2)}
    </span>
  );
}
