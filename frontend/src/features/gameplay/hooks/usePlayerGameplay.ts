import { useMutation } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { isApiClientError } from "@/api/apiError";
import { sessionApi } from "@/api/sessionApi";
import { useAnswerSubmission } from "@/features/gameplay/hooks/useAnswerSubmission";
import { useGameplay } from "@/features/gameplay/hooks/useGameplay";
import { useJoinSession } from "@/features/gameplay/hooks/useJoinSession";
import { useParticipantResult } from "@/features/gameplay/hooks/useParticipantResult";
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
    if (
      stored &&
      previousStatus.current !== "connected" &&
      gameplay.connectionStatus === "connected"
    ) {
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

  // The participant's own result only — never the full standings; the
  // rank and score are the server's verdicts (ADR-006), and other
  // players' rows never reach this device (live-event privacy).
  const resultQuery = useParticipantResult(
    stored?.sessionId,
    stored?.participantId,
    gameplay.phase
  );
  // Movement is a display diff of two consecutive server snapshots (the
  // PR #5 delta pattern, personal edition) — absent after a refresh. The
  // pair advances on data identity, not per render, so the delta survives
  // re-renders until the next snapshot arrives.
  const historyRef = useRef<{
    current?: typeof resultQuery.data;
    previous?: typeof resultQuery.data;
  }>({});
  if (resultQuery.data !== undefined && historyRef.current.current !== resultQuery.data) {
    historyRef.current = { previous: historyRef.current.current, current: resultQuery.data };
  }
  const previousResult = historyRef.current.previous;
  const scoreDelta =
    resultQuery.data !== undefined &&
    previousResult !== undefined &&
    previousResult.score !== undefined &&
    resultQuery.data.score !== undefined &&
    previousResult.sessionId === resultQuery.data.sessionId
      ? resultQuery.data.score - previousResult.score
      : undefined;
  const rankDelta =
    resultQuery.data !== undefined &&
    previousResult !== undefined &&
    previousResult.rank !== undefined &&
    resultQuery.data.rank !== undefined &&
    previousResult.sessionId === resultQuery.data.sessionId
      ? previousResult.rank - resultQuery.data.rank
      : undefined;

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
    personalResult: resultQuery.data,
    personalResultError: resultQuery.error,
    refetchPersonalResult: resultQuery.refetch,
    /** Points gained since the previous personal snapshot, when known. */
    scoreDelta,
    /** Positive = moved up since the previous personal snapshot. */
    rankDelta,
    ...gameplay,
    ...answerSubmission
  };
}
