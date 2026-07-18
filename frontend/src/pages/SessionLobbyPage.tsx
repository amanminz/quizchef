import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Button } from "@/components/common/Button";
import { ConfirmDialog } from "@/components/common/Dialog";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { Spinner } from "@/components/common/Spinner";
import { WorkflowHeader } from "@/components/common/WorkflowHeader";
import { GameConnectionBanner } from "@/features/gameplay/components/GameConnectionBanner";
import { useQuiz } from "@/features/quizzes/hooks/useQuiz";
import { ConfigurationSection } from "@/features/sessions/components/ConfigurationSection";
import { HostToolbar } from "@/features/sessions/components/HostToolbar";
import { JoinCodeCard } from "@/features/sessions/components/JoinCodeCard";
import { ParticipantWall } from "@/features/sessions/components/ParticipantWall";
import { PresentationToggle } from "@/features/sessions/components/PresentationToggle";
import { ReadinessPanel } from "@/features/sessions/components/ReadinessPanel";
import { SessionStatusBadge } from "@/features/sessions/components/SessionStatusBadge";
import { useLobby } from "@/features/sessions/hooks/useLobby";
import { usePresentationMode } from "@/features/sessions/hooks/usePresentationMode";
import { useRoster } from "@/features/sessions/hooks/useRoster";
import { useQuizTitle } from "@/features/sessions/hooks/useQuizTitle";

/**
 * The host's lobby — realtime-driven, and since the live-event polish the
 * room's projected face: a presentation mode strips the chrome, the
 * participant wall shows every joined name at projector-legible sizes,
 * and a readiness panel plus a confirm-before-start guard the one
 * irreversible click. Nothing here polls, and nothing transitions
 * optimistically: the page navigates to the session route only when the
 * server says IN_PROGRESS.
 */
export function SessionLobbyPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const lobby = useLobby(sessionId);
  const { data: quiz } = useQuiz(lobby.session?.publishedQuizVersionId);
  const quizTitle = useQuizTitle(lobby.session?.publishedQuizVersionId);
  const presentation = usePresentationMode();
  const rosterQuery = useRoster(sessionId, lobby.session?.state === "LOBBY");
  const [confirmStart, setConfirmStart] = useState(false);

  useEffect(() => {
    if (lobby.session?.state === "IN_PROGRESS") {
      navigate(`/sessions/${sessionId}/play`, { replace: true });
    }
  }, [lobby.session?.state, navigate, sessionId]);

  const participantCount = lobby.session?.participantCount ?? 0;

  const startSession = () => {
    setConfirmStart(false);
    if (sessionId) {
      // Navigation happens in the effect above once the confirmed summary
      // (or the session.started event) lands in the cache.
      void lobby.start(sessionId).catch(() => undefined);
    }
  };

  return (
    <PageContainer className={presentation.active ? "max-w-none px-8" : undefined}>
      {presentation.active ? (
        <div className="mb-4 flex items-start justify-between gap-4">
          <h1 className="text-2xl font-bold tracking-tight">
            {quizTitle ? `${quizTitle} — Lobby` : "Lobby"}
          </h1>
          <PresentationToggle presentation={presentation} />
        </div>
      ) : (
        <WorkflowHeader
          title={quizTitle ? `${quizTitle} — Lobby` : "Lobby"}
          backHref={`/sessions/${sessionId}`}
          backLabel="Session details"
          status={<SessionStatusBadge state={lobby.session?.state} />}
          actions={<PresentationToggle presentation={presentation} />}
        />
      )}

      <GameConnectionBanner status={lobby.connectionStatus} />

      {lobby.isLoading && (
        <div className="flex justify-center py-16">
          <Spinner size="lg" className="text-primary" />
        </div>
      )}

      {lobby.error != null && (
        <ErrorPanel error={lobby.error} onRetry={() => void lobby.refetch()} />
      )}

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
            canStart={lobby.canStart && !lobby.isStarting}
            startDisabledReason="At least one participant must join before the session can start."
            onStart={() => setConfirmStart(true)}
            isStarting={lobby.isStarting}
          />

          {lobby.startError != null && (
            <div className="mb-6">
              <ErrorPanel title="Could not start the session" error={lobby.startError} />
            </div>
          )}

          <div
            className={
              presentation.active
                ? "grid gap-8 lg:grid-cols-[minmax(16rem,1fr)_3fr]"
                : "grid gap-6 lg:grid-cols-[1fr_1.5fr]"
            }
          >
            <div className="flex flex-col gap-4">
              <JoinCodeCard
                sessionPin={lobby.session.sessionPin}
                quizTitle={quizTitle}
                presentation={presentation.active}
              />
              <ReadinessPanel
                connectionStatus={lobby.connectionStatus}
                quizTitle={quizTitle}
                questionCount={quiz?.questionIds?.length}
                playerCount={participantCount}
              />
              {!presentation.active && <ConfigurationSection settings={lobby.session.settings} />}
            </div>
            <ParticipantWall
              participants={rosterQuery.data?.participants ?? []}
              totalCount={participantCount}
              announcement={lobby.announcement}
              presentation={presentation.active}
            />
          </div>

          <ConfirmDialog
            open={confirmStart}
            title={`Start the quiz for ${participantCount} player${participantCount === 1 ? "" : "s"}?`}
            description={
              lobby.session.settings?.allowLateJoin
                ? "Late join is enabled — players can still join after the quiz starts."
                : "Late join is disabled — players may not be able to join after this point."
            }
            confirmLabel="Start session"
            isConfirming={lobby.isStarting}
            onConfirm={startSession}
            onCancel={() => setConfirmStart(false)}
          />
        </>
      )}
    </PageContainer>
  );
}
