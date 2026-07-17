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
  "participant.reconnected"
]);

const QUESTION_PROGRESSION_EVENTS = new Set<ProtocolMessage["type"]>([
  "question.started",
  "question.closed",
  "answer.revealed",
  "leaderboard.updated"
]);

function announcementFor(message: ProtocolMessage): string | null {
  switch (message.type) {
    case "question.started":
      return "A new question has started.";
    case "question.closed":
      return "The question has closed.";
    case "session.started":
      return "The session has started.";
    case "session.finished":
      return "The session has finished.";
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
    },
    [queryClient, sessionId]
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

  return { ...state, connectionStatus, announcement };
}
