import { useMutation } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { isApiClientError } from "@/api/apiError";
import { sessionApi } from "@/api/sessionApi";
import { useAnswerSubmission } from "@/features/gameplay/hooks/useAnswerSubmission";
import { useGameplay } from "@/features/gameplay/hooks/useGameplay";
import { useJoinSession } from "@/features/gameplay/hooks/useJoinSession";
import { useFinalPlacement } from "@/features/gameplay/hooks/useFinalPlacement";
import { useParticipantResult } from "@/features/gameplay/hooks/useParticipantResult";
import { isLastQuestion } from "@/features/gameplay/isLastQuestion";
import {
  usePlayerSessionStore,
  useStoredPlayerSession
} from "@/features/gameplay/playerSessionStore";
import type { JoinSessionRequest } from "@/types/api";

/**
 * Participant-only gameplay orchestration for one PIN. Distinct from
 * `useGameHost` by design (shared presentational components, never shared
 * orchestration): a participant has a join step the host doesn't, submits
 * answers the host never does, and recovers through `resumeParticipant` —
 * the server's replay contract (RFC-005) — rather than a plain re-fetch,
 * because only resume restores what this participant already submitted for
 * the question in play.
 *
 * Resume runs whenever a stored credential for this PIN exists: right
 * after joining, on a fresh page load, and again whenever the realtime
 * connection comes back after dropping. Every arrival is treated as a
 * possible return, and resume is always tried *before* the join form is
 * offered — a returning player who is asked to join again becomes a second
 * participant with none of their score, which is the whole failure this
 * flow exists to prevent.
 *
 * A rejected credential is forgotten rather than retried. It means the
 * server does not recognize this player in the session live under this PIN
 * — an unknown token, or one left over from an earlier quiz that reused
 * the code — and in both cases the honest next step is the join form.
 */
export function usePlayerGameplay(pin: string) {
  const stored = useStoredPlayerSession(pin);
  const clearStored = usePlayerSessionStore((state) => state.clear);
  const joinMutation = useJoinSession();

  const resumeMutation = useMutation({
    mutationFn: () =>
      sessionApi.resumeParticipant(pin, { resumeToken: stored?.resumeToken }),
    onError: (error) => {
      // The server does not recognize this credential in the session live
      // under this PIN — an unknown or revoked token, or one belonging to
      // an earlier quiz that happened to use the same six digits. Forget
      // it so the join form reappears instead of retrying forever.
      if (isApiClientError(error) && error.code === "session.participant.not-found") {
        clearStored(stored?.sessionId ?? "");
      }
    }
  });

  const gameplay = useGameplay(stored?.sessionId, stored?.participantId);

  useEffect(() => {
    if (stored) {
      resumeMutation.mutate();
    }
    // Re-run only when the identity of the stored record actually changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stored?.sessionId, stored?.participantId]);

  const previousStatus = useRef(gameplay.connectionStatus);
  useEffect(() => {
    if (
      stored &&
      previousStatus.current !== "connected" &&
      gameplay.connectionStatus === "connected"
    ) {
      resumeMutation.mutate();
    }
    previousStatus.current = gameplay.connectionStatus;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [gameplay.connectionStatus, stored]);

  const answerSubmission = useAnswerSubmission(
    stored?.sessionId,
    stored?.participantId,
    gameplay.question,
    resumeMutation.data
  );

  // Held back on the quiz's last question — the backend's final-rank hold
  // applies one host click before the session technically finishes, not
  // only once it's FINISHED-but-not-released (see useParticipantResult).
  const onLastQuestion = isLastQuestion(gameplay.question);

  // Their own progress only: what this question awarded them and their
  // running total, both the server's numbers (ADR-006). No rank — the
  // response has none on it — and no other player's row ever reaches this
  // device. `pointsEarned` comes from the server rather than a diff of two
  // client snapshots, so it survives a refresh mid-reveal.
  const resultQuery = useParticipantResult(
    stored?.sessionId,
    stored?.participantId,
    gameplay.phase,
    onLastQuestion
  );

  // How they finished. Fires only once the host has released results —
  // never during the ceremony, so no placement is ever sitting in this
  // device's cache while the room is still watching the podium.
  const finalPlacementQuery = useFinalPlacement(
    stored?.sessionId,
    stored?.participantId,
    gameplay.phase
  );

  const join = (request: JoinSessionRequest) => joinMutation.mutateAsync({ pin, request });

  // Pruned only once the session is archived — not when it merely
  // finishes. A finished session is still being read: the podium runs, the
  // host releases results, and the player refreshes to see where they came.
  // Dropping the credential at FINISHED would lose them their own result
  // for the sake of tidying storage a few minutes early.
  const sessionState = gameplay.session?.state;
  const storedSessionId = stored?.sessionId;
  useEffect(() => {
    if (sessionState === "ARCHIVED" && storedSessionId) {
      clearStored(storedSessionId);
    }
  }, [sessionState, storedSessionId, clearStored]);

  const resumed = resumeMutation.data;

  return {
    hasJoined: stored !== undefined,
    participantId: stored?.participantId,
    // The server's copy wins once resume has answered. A device that has
    // been away, or a record written by an older client, can disagree with
    // what the room actually has on the board (ADR-006).
    displayName: resumed?.displayName ?? stored?.displayName,
    preferredLanguage: resumed?.preferredLanguage ?? stored?.preferredLanguage,
    join,
    isJoining: joinMutation.isPending,
    joinError: joinMutation.error,
    isReconnecting: resumeMutation.isPending && resumeMutation.data === undefined,
    reconnectError: resumeMutation.error,
    retryReconnect: () => resumeMutation.mutate(),
    personalResult: resultQuery.data,
    personalResultError: resultQuery.error,
    refetchPersonalResult: resultQuery.refetch,
    /** How they finished — exact or relative; only after the host releases. */
    finalPlacement: finalPlacementQuery.data,
    finalPlacementError: finalPlacementQuery.error,
    refetchFinalPlacement: finalPlacementQuery.refetch,
    ...gameplay,
    ...answerSubmission
  };
}
