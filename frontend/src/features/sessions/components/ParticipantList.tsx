import { ParticipantAvatar } from "@/features/sessions/components/ParticipantAvatar";
import { WaitingState } from "@/features/sessions/components/WaitingState";
import type { LobbyParticipant } from "@/features/sessions/types";

export interface ParticipantListProps {
  /** Presence known from realtime events since this view subscribed. */
  participants: LobbyParticipant[];
  /** The server's authoritative roster size (session summary). */
  totalCount: number;
  /** The latest roster change, announced to assistive tech. */
  announcement: string;
}

/**
 * The lobby roster. Realtime presence drives the entries; the server's
 * participantCount stays the headline number. Participants who joined
 * before this view subscribed are in the count but not in the event-built
 * list — that difference is rendered as its own row rather than papered
 * over (there is no roster read endpoint yet, RFC-004 Future Work).
 */
export function ParticipantList({ participants, totalCount, announcement }: ParticipantListProps) {
  const unseenCount = Math.max(0, totalCount - participants.length);

  return (
    <section aria-label="Participants">
      <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
        Participants ({totalCount})
      </h2>
      <div aria-live="polite" role="status" className="sr-only">
        {announcement}
      </div>

      {totalCount === 0 ? (
        <WaitingState />
      ) : (
        <ul className="flex flex-col gap-2">
          {unseenCount > 0 && (
            <li className="flex items-center gap-3 rounded-md border border-dashed px-3 py-2 text-sm text-muted-foreground">
              {unseenCount} joined before this view opened
            </li>
          )}
          {participants.map((participant) => (
            <li
              key={participant.participantId}
              className="flex items-center gap-3 rounded-md border px-3 py-2"
            >
              <ParticipantAvatar
                participantId={participant.participantId}
                status={participant.status}
              />
              <span className="min-w-0 flex-1 truncate text-sm font-medium">
                Player {participant.participantId.slice(0, 8)}
              </span>
              <span
                className={
                  participant.status === "connected"
                    ? "text-xs text-success"
                    : "text-xs text-muted-foreground"
                }
              >
                {participant.status === "connected" ? "Connected" : "Disconnected"}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
