import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useRef, useState } from "react";
import { useGameplaySubscriptions } from "@/features/gameplay/gameplaySubscriptions";
import { useGameplayState } from "@/features/gameplay/hooks/useGameplayState";
import { gameplayKeys } from "@/features/gameplay/queryKeys";
import { sessionKeys } from "@/features/sessions/queryKeys";
import { useConnectionStore } from "@/realtime/connectionStore";
import { useRealtimeClient } from "@/realtime/useRealtime";
import type { ProtocolMessage } from "@/types/protocol";

const SESSION_LIFECYCLE_EVENTS = new Set<ProtocolMessage["type"]>([
  "session.started",
  "session.finished",
  "participant.joined",
  "participant.disconnected",
  "participant.reconnected",
  "final.results.revealed"
]);

const QUESTION_PROGRESSION_EVENTS = new Set<ProtocolMessage["type"]>([
  "question.preview.started",
  "question.started",
  "question.closed",
  "answer.revealed",
  "leaderboard.updated",
  // Both change what the question in play *is*: a correction replaces its
  // wording (and may replay it from the reading period), a removal advances
  // past it entirely. Neither carries the new state, so both are read back.
  "question.corrected",
  "question.removed"
]);

/** Events after which the standings read may have new content. */
const RESULTS_EVENTS = new Set<ProtocolMessage["type"]>([
  "answer.revealed",
  "leaderboard.updated",
  "session.finished",
  // A correction or removal cancels a question's answers and reverses the
  // points they earned, so every projection over those answers moved —
  // scores, ranks, the distribution. The backend did the reversing
  // (ADR-006); this only stops us rendering the standings from before it.
  "question.corrected",
  "question.removed"
]);

function announcementFor(message: ProtocolMessage): string | null {
  switch (message.type) {
    case "question.preview.started":
      return "A new question is about to begin. Get ready to read it.";
    case "question.started":
      return "The answer options are now available.";
    case "question.closed":
      return "The question has closed.";
    case "question.corrected":
      return "The Quiz Master corrected this question. It is starting again.";
    case "question.removed":
      return "The Quiz Master removed this question. Get ready for the next one.";
    case "answer.revealed":
      return "The correct answer has been revealed.";
    case "leaderboard.updated":
      return "The leaderboard has been updated.";
    case "session.started":
      return "The session has started.";
    case "session.finished":
      return "The quiz is complete.";
    case "final.results.revealed":
      return "Final results have been released.";
    default:
      return null;
  }
}

/**
 * The shared gameplay orchestration every gameplay screen builds on
 * (host and participant alike): connects realtime for the lifetime of the
 * screen, subscribes through `useGameplaySubscriptions` (pages never
 * subscribe directly), and reconciles server state on every relevant
 * event — a push only tells us *that* something changed; the session
 * summary and current-question queries are re-fetched to learn *what* it
 * now is (ADR-006), exactly like the lobby's realtime pattern (PR #3).
 *
 * On reconnect (the connection dropping and coming back), both queries are
 * refetched again so the UI converges to the backend's truth without
 * relying on events that may have been missed while disconnected — the
 * "design for reconnects" recommendation, generalized below the FSM.
 */
export function useGameplay(sessionId: string | undefined, participantId?: string) {
  const queryClient = useQueryClient();
  const client = useRealtimeClient();
  const connectionStatus = useConnectionStore((state) => state.status);
  const state = useGameplayState(sessionId);
  const [announcement, setAnnouncement] = useState("");
  // A question vanishing needs explaining, and the explanation has to
  // outlive the event that triggered it: the next question's reading period
  // begins immediately, and a player who looked down would otherwise see
  // only a screen that changed for no reason.
  const [questionRemoved, setQuestionRemoved] = useState(false);

  useEffect(() => {
    client.connect();
    return () => {
      void client.disconnect();
    };
  }, [client]);

  const onEvent = useCallback(
    (message: ProtocolMessage) => {
      const text = announcementFor(message);
      if (text) {
        setAnnouncement(text);
      }
      // Deliberately *not* cleared by the next question's reading period:
      // that period is where the notice belongs, standing in for the usual
      // "options coming shortly" so the player is told why the question
      // changed under them. It clears when that question actually opens.
      if (message.type === "question.removed") {
        setQuestionRemoved(true);
      } else if (message.type === "question.started" || message.type === "session.finished") {
        setQuestionRemoved(false);
      }
      if (!sessionId) {
        return;
      }
      if (SESSION_LIFECYCLE_EVENTS.has(message.type)) {
        void queryClient.invalidateQueries({ queryKey: sessionKeys.detail(sessionId) });
      }
      if (QUESTION_PROGRESSION_EVENTS.has(message.type)) {
        void queryClient.invalidateQueries({ queryKey: sessionKeys.detail(sessionId) });
        void queryClient.invalidateQueries({ queryKey: gameplayKeys.currentQuestion(sessionId) });
      }
      // Host-only answer progress: the broadcast carries no counts (and
      // no participant) — it only says the authoritative read moved.
      // Roster events move the eligible denominator the same way. A
      // participant device (participantId set) never mounts this query,
      // so it has nothing to invalidate.
      if (
        !participantId &&
        (message.type === "answer.progress" ||
          message.type === "question.started" ||
          message.type === "question.corrected" ||
          message.type === "question.removed" ||
          message.type === "participant.joined" ||
          message.type === "participant.disconnected" ||
          message.type === "participant.reconnected")
      ) {
        void queryClient.invalidateQueries({
          queryKey: gameplayKeys.answerProgress(sessionId)
        });
      }
      if (RESULTS_EVENTS.has(message.type)) {
        // Role-specific invalidation (live-event privacy): a participant
        // device refreshes only its own personal result; the host (no
        // participantId) refreshes the full-standings read. Neither role
        // ever mounts the other's query.
        if (participantId) {
          void queryClient.invalidateQueries({
            queryKey: gameplayKeys.personalResult(sessionId, participantId)
          });
        } else {
          void queryClient.invalidateQueries({ queryKey: gameplayKeys.results(sessionId) });
          // The host's own question panel renumbers with the sequence.
          void queryClient.invalidateQueries({ queryKey: sessionKeys.questions(sessionId) });
          // The projected Top 5 rides the same signal. Invalidated by
          // session prefix, not by question: the reveal that matters is
          // the one that just happened, and the stale entries under it
          // belong to questions already played.
          void queryClient.invalidateQueries({
            queryKey: gameplayKeys.topFiveTransitions(sessionId)
          });
        }
      }
    },
    [queryClient, sessionId, participantId]
  );

  useGameplaySubscriptions(sessionId, participantId, onEvent);

  const previousStatus = useRef(connectionStatus);
  const refetchSession = state.refetchSession;
  const refetchQuestion = state.refetchQuestion;
  useEffect(() => {
    if (previousStatus.current !== "connected" && connectionStatus === "connected") {
      void refetchSession();
      void refetchQuestion();
    }
    previousStatus.current = connectionStatus;
  }, [connectionStatus, refetchSession, refetchQuestion]);

  return { ...state, connectionStatus, announcement, questionRemoved };
}
