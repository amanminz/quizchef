import { useMutation } from "@tanstack/react-query";
import { useCallback, useEffect, useRef, useState } from "react";
import { sessionApi } from "@/api/sessionApi";
import { useAnswerSubmission } from "@/features/gameplay/hooks/useAnswerSubmission";
import { useGameplay } from "@/features/gameplay/hooks/useGameplay";
import { useJoinSession } from "@/features/gameplay/hooks/useJoinSession";
import { useFinalPlacement } from "@/features/gameplay/hooks/useFinalPlacement";
import { useParticipantRecovery } from "@/features/gameplay/hooks/useParticipantRecovery";
import { useParticipantResult } from "@/features/gameplay/hooks/useParticipantResult";
import { isLastQuestion } from "@/features/gameplay/isLastQuestion";
import {
  classifyResumeFailure,
  resumeRetryDelayMs
} from "@/features/gameplay/resumeOutcome";
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

  // Set only when the server has definitively refused the credential, and
  // never on a failure that might be the network. It is what turns the
  // screen into a recovery offer instead of a bare join form.
  const [credentialRejected, setCredentialRejected] = useState(false);
  const retryAttempt = useRef(0);
  const retryTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const [welcomeBack, setWelcomeBack] = useState<{ displayName: string; score: number } | null>(
    null
  );
  const welcomeTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const resumeMutation = useMutation({
    mutationFn: () =>
      sessionApi.resumeParticipant(pin, { resumeToken: stored?.resumeToken }),
    onSuccess: (snapshot) => {
      retryAttempt.current = 0;
      setCredentialRejected(false);
      // Only worth saying when there is something to reassure them about.
      // On a first join, or a refresh before anyone has scored, "welcome
      // back" is noise.
      if ((snapshot.participantScore ?? 0) > 0 && snapshot.displayName) {
        setWelcomeBack({ displayName: snapshot.displayName, score: snapshot.participantScore! });
        clearTimeout(welcomeTimer.current);
        welcomeTimer.current = setTimeout(() => setWelcomeBack(null), 6_000);
      }
    },
    onError: (error) => {
      const failure = classifyResumeFailure(error);
      if (failure.kind === "credential-rejected") {
        // This credential will never work — but the player is NOT sent to a
        // blank join form, because typing their own name there is exactly
        // what produces "that name is already taken". They are offered
        // recovery instead, and the credential is kept until they choose:
        // clearing it here would leave nothing to identify them with if the
        // rejection turns out to be about the wrong session.
        setCredentialRejected(true);
        return;
      }
      // Temporary: the request did not land, or the venue's shared address
      // hit a limit while the whole room reconnected at once. The
      // credential is untouched and we simply try again — losing a live
      // participation to a passing network blip is the failure this whole
      // path exists to prevent.
      retryAttempt.current += 1;
      const delay = resumeRetryDelayMs(retryAttempt.current, failure.retryAfterSeconds);
      clearTimeout(retryTimer.current);
      retryTimer.current = setTimeout(() => resumeMutation.mutate(), delay);
    }
  });

  useEffect(
    () => () => {
      clearTimeout(retryTimer.current);
      clearTimeout(welcomeTimer.current);
    },
    []
  );

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

  // Recovery writes a fresh credential, so a successful redemption ends the
  // rejected state by itself — the next render simply has a record again.
  const recoveryMutation = useParticipantRecovery(pin);
  const recoverWithCode = useCallback(
    async (code: string) => {
      const recovered = await recoveryMutation.mutateAsync(code);
      setCredentialRejected(false);
      retryAttempt.current = 0;
      return recovered;
    },
    // recoveryMutation is stable for the life of the hook.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    []
  );

  /** Forgets the refused credential and shows the join form deliberately. */
  const startOver = useCallback(() => {
    if (stored?.sessionId) {
      clearStored(stored.sessionId);
    }
    setCredentialRejected(false);
  }, [clearStored, stored?.sessionId]);

  const retryResume = useCallback(() => {
    retryAttempt.current = 0;
    setCredentialRejected(false);
    resumeMutation.mutate();
    // resumeMutation is stable for the life of the hook.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
    /**
     * The server has refused this credential outright. The screen offers
     * recovery; it must never silently become the join form, which is what
     * turned a lost credential into "that name is already taken".
     */
    credentialRejected,
    /** Set briefly after a resume that restored real points. */
    welcomeBack,
    startOver,
    retryResume,
    /** Redeems the host's code and stores the credential it returns. */
    recoverWithCode,
    isRecovering: recoveryMutation.isPending,
    recoveryError: recoveryMutation.error,
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
