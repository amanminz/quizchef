import { useEffect } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Button } from "@/components/common/Button";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { Spinner } from "@/components/common/Spinner";
import { WorkflowHeader } from "@/components/common/WorkflowHeader";
import { ConfigurationSection } from "@/features/sessions/components/ConfigurationSection";
import { HostToolbar } from "@/features/sessions/components/HostToolbar";
import { JoinCodeCard } from "@/features/sessions/components/JoinCodeCard";
import { ParticipantList } from "@/features/sessions/components/ParticipantList";
import { SessionStatusBadge } from "@/features/sessions/components/SessionStatusBadge";
import { useLobby } from "@/features/sessions/hooks/useLobby";
import { useQuizTitle } from "@/features/sessions/hooks/useQuizTitle";

/**
 * The host's lobby — the realtime heart of this PR. The session summary
 * establishes the initial state; from then on STOMP events drive presence
 * and lifecycle (useLobby). Nothing here polls, and nothing transitions
 * optimistically: the page navigates to the session route only when the
 * server says IN_PROGRESS — whether that truth arrived as the start
 * request's response or as a session.started broadcast.
 */
export function SessionLobbyPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const lobby = useLobby(sessionId);
  const quizTitle = useQuizTitle(lobby.session?.publishedQuizVersionId);

  useEffect(() => {
    if (lobby.session?.state === "IN_PROGRESS") {
      navigate(`/sessions/${sessionId}/play`, { replace: true });
    }
  }, [lobby.session?.state, navigate, sessionId]);

  const startSession = () => {
    if (sessionId) {
      // Navigation happens in the effect above once the confirmed summary
      // (or the session.started event) lands in the cache.
      void lobby.start(sessionId).catch(() => undefined);
    }
  };

  return (
    <PageContainer>
      <WorkflowHeader
        title={quizTitle ? `${quizTitle} — Lobby` : "Lobby"}
        backHref={`/sessions/${sessionId}`}
        backLabel="Session details"
        status={<SessionStatusBadge state={lobby.session?.state} />}
      />

      {(lobby.connectionStatus === "reconnecting" ||
        lobby.connectionStatus === "disconnected") && (
        <div
          role="alert"
          className="mb-4 rounded-md border border-destructive/40 bg-destructive/5 px-4 py-3 text-sm"
        >
          Realtime connection lost — updates are paused while we reconnect. The session itself is
          unaffected.
        </div>
      )}

      {lobby.isLoading && (
        <div className="flex justify-center py-16">
          <Spinner size="lg" className="text-primary" />
        </div>
      )}

      {lobby.error != null && <ErrorPanel error={lobby.error} onRetry={() => void lobby.refetch()} />}

      {lobby.session?.state === "CREATED" && (
        <div className="mb-6 flex flex-col items-center gap-4 rounded-lg border border-dashed px-6 py-12 text-center">
          <p className="text-sm text-muted-foreground">
            The lobby is not open yet — open it so participants can join.
          </p>
          <Button
            onClick={() =>
              lobby.session?.sessionPin &&
              void lobby.openLobby(lobby.session.sessionPin).catch(() => undefined)
            }
            isLoading={lobby.isOpeningLobby}
          >
            Open Lobby
          </Button>
          {lobby.openLobbyError != null && (
            <ErrorPanel title="Could not open the lobby" error={lobby.openLobbyError} />
          )}
        </div>
      )}

      {lobby.session?.state === "FINISHED" && (
        <div className="mb-6 rounded-lg border border-dashed px-6 py-12 text-center text-sm text-muted-foreground">
          This session has finished.
        </div>
      )}

      {lobby.session?.state === "LOBBY" && (
        <>
          <HostToolbar
            connectionStatus={lobby.connectionStatus}
            canStart={lobby.canStart}
            startDisabledReason="At least one participant must join before the session can start."
            onStart={startSession}
            isStarting={lobby.isStarting}
          />

          {lobby.startError != null && (
            <div className="mb-6">
              <ErrorPanel title="Could not start the session" error={lobby.startError} />
            </div>
          )}

          <div className="grid gap-6 lg:grid-cols-[1fr_1.5fr]">
            <div className="flex flex-col gap-4">
              <JoinCodeCard sessionPin={lobby.session.sessionPin} />
              <ConfigurationSection settings={lobby.session.settings} />
            </div>
            <ParticipantList
              participants={lobby.participants}
              totalCount={lobby.session.participantCount ?? 0}
              announcement={lobby.announcement}
            />
          </div>
        </>
      )}
    </PageContainer>
  );
}
