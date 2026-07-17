import { useMutation } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { isApiClientError } from "@/api/apiError";
import { sessionApi } from "@/api/sessionApi";
import { useAnswerSubmission } from "@/features/gameplay/hooks/useAnswerSubmission";
import { useGameplay } from "@/features/gameplay/hooks/useGameplay";
import { useJoinSession } from "@/features/gameplay/hooks/useJoinSession";
import { useResults } from "@/features/gameplay/hooks/useResults";
import { usePlayerSessionStore } from "@/features/gameplay/playerSessionStore";
import type { JoinSessionRequest } from "@/types/api";

/**
 * Participant-only gameplay orchestration for one PIN. Distinct from
 * `useGameHost` by design (shared presentational components, never shared
 * orchestration): a participant has a join step the host doesn't, submits
 * answers the host never does, and recovers through `reconnect` — the
 * server's replay contract (RFC-005) — rather than a plain re-fetch,
 * because only `reconnect` restores what this participant already
 * submitted for the question in play.
 *
 * `reconnect` runs once whenever a join record for this PIN exists (right
 * after joining, on a fresh page load, and again whenever the realtime
 * connection comes back after dropping) — "design for reconnects from the
 * start": every mount is treated as a possible return, refetching state
 * before resuming realtime rather than trusting local history.
 */
export function usePlayerGameplay(pin: string) {
  const stored = usePlayerSessionStore((state) => state.bySessionPin[pin]);
  const clearStored = usePlayerSessionStore((state) => state.clear);
  const joinMutation = useJoinSession();

  const reconnectMutation = useMutation({
    mutationFn: () =>
      sessionApi.reconnect(
        stored?.guestParticipantToken
          ? { guestParticipantToken: stored.guestParticipantToken }
          : { sessionId: stored?.sessionId }
      ),
    onError: (error) => {
      // The stored record no longer resolves to a real participant (an
      // invalid/expired guest token, or the session is gone) — forget it
      // so the join form reappears instead of retrying forever.
      if (isApiClientError(error) && error.code === "session.participant.not-found") {
        clearStored(pin);
      }
    }
  });

  const gameplay = useGameplay(stored?.sessionId, stored?.participantId);

  useEffect(() => {
    if (stored) {
      reconnectMutation.mutate();
    }
    // Re-run only when the identity of the stored record actually changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stored?.sessionId, stored?.participantId]);

  const previousStatus = useRef(gameplay.connectionStatus);
  useEffect(() => {
    if (stored && previousStatus.current !== "connected" && gameplay.connectionStatus === "connected") {
      reconnectMutation.mutate();
    }
    previousStatus.current = gameplay.connectionStatus;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [gameplay.connectionStatus, stored]);

  const answerSubmission = useAnswerSubmission(
    stored?.sessionId,
    stored?.participantId,
    gameplay.question,
    reconnectMutation.data
  );

  const resultsQuery = useResults(stored?.sessionId, gameplay.phase);
  // The participant's own row in the server's standings — a lookup, never
  // a computation: rank and score are the server's verdicts (ADR-006).
  const ownEntry = resultsQuery.data?.entries?.find(
    (entry) => entry.participantId === stored?.participantId
  );

  const join = (request: JoinSessionRequest) => joinMutation.mutateAsync({ pin, request });

  return {
    hasJoined: stored !== undefined,
    participantId: stored?.participantId,
    displayName: stored?.displayName,
    preferredLanguage: stored?.preferredLanguage,
    join,
    isJoining: joinMutation.isPending,
    joinError: joinMutation.error,
    isReconnecting: reconnectMutation.isPending && reconnectMutation.data === undefined,
    reconnectError: reconnectMutation.error,
    retryReconnect: () => reconnectMutation.mutate(),
    results: resultsQuery.data,
    resultsError: resultsQuery.error,
    refetchResults: resultsQuery.refetch,
    ownEntry,
    ...gameplay,
    ...answerSubmission
  };
}
