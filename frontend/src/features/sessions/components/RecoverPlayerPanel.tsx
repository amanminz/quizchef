import { ChevronDown, ChevronRight, LifeBuoy, WifiOff } from "lucide-react";
import { useState } from "react";
import { errorMessage } from "@/api/apiError";
import { Button } from "@/components/common/Button";
import { Modal } from "@/components/common/Modal";
import { useIssueRecoveryCode } from "@/features/sessions/hooks/useIssueRecoveryCode";
import { useRoster } from "@/features/sessions/hooks/useRoster";
import type { SessionParticipantDto } from "@/types/api";

export interface RecoverPlayerPanelProps {
  sessionId: string | undefined;
}

/**
 * The host's tool for a player who cannot get back in.
 *
 * <p>Deliberately not part of the projected participant wall, and never
 * rendered in presentation mode. The whole output of this panel is a code
 * that lets whoever types it become that player — putting it on the screen
 * the room is looking at would defeat the point of having it at all.
 *
 * <p>Collapsed by default because it is for the exception, not the event:
 * a host runs a whole quiz without opening it, and the one time they need
 * it, someone is standing in front of them.
 */
export function RecoverPlayerPanel({ sessionId }: RecoverPlayerPanelProps) {
  const [expanded, setExpanded] = useState(false);
  const [recovering, setRecovering] = useState<SessionParticipantDto | null>(null);
  const rosterQuery = useRoster(sessionId, expanded);
  const issue = useIssueRecoveryCode(sessionId);

  const participants = rosterQuery.data?.participants ?? [];
  // Disconnected players first: the person asking for help is, almost by
  // definition, the one the server has lost track of.
  const ordered = [...participants].sort(
    (a, b) => Number(a.connected ?? false) - Number(b.connected ?? false)
  );

  const openFor = async (participant: SessionParticipantDto) => {
    setRecovering(participant);
    issue.reset();
    await issue.mutateAsync(participant.participantId ?? "");
  };

  return (
    <section className="mb-4 rounded-lg border bg-card">
      <button
        type="button"
        onClick={() => setExpanded((open) => !open)}
        aria-expanded={expanded}
        className="flex w-full items-center gap-2 px-4 py-3 text-left text-sm font-medium"
      >
        {expanded ? (
          <ChevronDown aria-hidden className="h-4 w-4" />
        ) : (
          <ChevronRight aria-hidden className="h-4 w-4" />
        )}
        <LifeBuoy aria-hidden className="h-4 w-4 text-muted-foreground" />
        Help a player rejoin
      </button>

      {expanded && (
        <div className="space-y-2 border-t px-4 py-3">
          <p className="text-sm text-muted-foreground">
            If someone lost their game — cleared their browser, or switched phones — give them a
            recovery code. Their score and answers come back with them.
          </p>
          <ul className="space-y-1">
            {ordered.map((participant) => (
              <li
                key={participant.participantId}
                className="flex items-center gap-3 rounded-md px-2 py-2 text-sm"
              >
                <span className="min-w-0 flex-1 truncate font-medium">
                  {participant.displayName}
                </span>
                {!participant.connected && (
                  <span className="flex shrink-0 items-center gap-1 text-xs text-muted-foreground">
                    <WifiOff aria-hidden className="h-3 w-3" />
                    Disconnected
                  </span>
                )}
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => void openFor(participant)}
                  isLoading={issue.isPending && recovering?.participantId === participant.participantId}
                >
                  Recover player
                </Button>
              </li>
            ))}
          </ul>
          {ordered.length === 0 && (
            <p className="text-sm text-muted-foreground">Nobody has joined yet.</p>
          )}
        </div>
      )}

      <Modal
        open={recovering != null}
        onClose={() => setRecovering(null)}
        title={`Recovery code for ${recovering?.displayName ?? "this player"}`}
      >
        <div className="space-y-3">
          {issue.isPending && <p className="text-sm text-muted-foreground">Generating…</p>}
          {issue.error != null && (
            <p role="alert" className="text-sm text-destructive">
              {errorMessage(issue.error)}
            </p>
          )}
          {issue.data?.code && (
            <>
              <p className="text-sm text-muted-foreground">
                Read this out to {recovering?.displayName}. It works once, for about five
                minutes.
              </p>
              <p className="text-center font-mono text-4xl font-bold tracking-[0.3em]">
                {issue.data.code}
              </p>
              <p className="text-xs text-muted-foreground">
                They should tap &ldquo;Enter recovery code&rdquo; on their phone — not join
                again, which would start them at zero.
              </p>
            </>
          )}
          <div className="flex justify-end">
            <Button variant="outline" onClick={() => setRecovering(null)}>
              Done
            </Button>
          </div>
        </div>
      </Modal>
    </section>
  );
}
