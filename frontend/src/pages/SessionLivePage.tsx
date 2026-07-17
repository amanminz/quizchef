import { Gamepad2, Users } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { Button } from "@/components/common/Button";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { Spinner } from "@/components/common/Spinner";
import { WorkflowHeader } from "@/components/common/WorkflowHeader";
import { AnswerGrid } from "@/features/gameplay/components/AnswerGrid";
import { CountdownOverlay } from "@/features/gameplay/components/CountdownOverlay";
import { GameConnectionBanner } from "@/features/gameplay/components/GameConnectionBanner";
import { QuestionCard } from "@/features/gameplay/components/QuestionCard";
import { QuestionSkeleton } from "@/features/gameplay/components/QuestionSkeleton";
import { QuestionTransition } from "@/features/gameplay/components/QuestionTransition";
import { useGameHost } from "@/features/gameplay/hooks/useGameHost";
import { SessionStatusBadge } from "@/features/sessions/components/SessionStatusBadge";
import { useQuizTitle } from "@/features/sessions/hooks/useQuizTitle";

/**
 * The host's gameplay screen. Host never answers questions — the options
 * render read-only, purely so the host sees exactly what participants
 * see — and monitors participant count, progress, and the timer. The one
 * action, `nextStep`, sequences whatever host commands the current phase
 * requires (see `useGameHost`); it is never optimistic, and its result is
 * always what the next render reflects, never an assumption made here.
 */
export function SessionLivePage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const host = useGameHost(sessionId);
  const quizTitle = useQuizTitle(host.session?.publishedQuizVersionId);

  return (
    <PageContainer>
      <WorkflowHeader
        title={quizTitle ?? "Live session"}
        backHref="/sessions"
        backLabel="Back to sessions"
        status={<SessionStatusBadge state={host.session?.state} />}
        actions={
          host.canAdvance ? (
            <Button onClick={() => void host.nextStep()} isLoading={host.isAdvancing}>
              {host.nextStepLabel}
            </Button>
          ) : undefined
        }
      />

      <GameConnectionBanner status={host.connectionStatus} />
      <div aria-live="polite" role="status" className="sr-only">
        {host.announcement}
      </div>

      {(host.isLoadingSession || host.isLoadingQuestion) && (
        <div className="flex justify-center py-16">
          <Spinner size="lg" className="text-primary" />
        </div>
      )}

      {host.sessionError != null && (
        <ErrorPanel error={host.sessionError} onRetry={() => void host.refetchSession()} />
      )}

      {host.nextStepError != null && (
        <div className="mb-6">
          <ErrorPanel title="Could not advance the game" error={host.nextStepError} />
        </div>
      )}

      {!host.isLoadingSession && host.sessionError == null && (
        <HostGameplayBody host={host} />
      )}
    </PageContainer>
  );
}

function HostGameplayBody({ host }: { host: ReturnType<typeof useGameHost> }) {
  switch (host.phase) {
    case "LOBBY":
      return (
        <EmptyState
          icon={Gamepad2}
          title="The session hasn't started"
          description="Open the lobby and start the session before gameplay begins."
          action={
            <Link to={`/sessions/${host.session?.sessionId ?? ""}`}>
              <Button variant="secondary">Session details</Button>
            </Link>
          }
        />
      );
    case "COUNTDOWN":
      return <CountdownOverlay />;
    case "QUESTION_OPEN":
    case "WAITING":
      if (!host.question) {
        return <QuestionSkeleton />;
      }
      return (
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Users aria-hidden className="h-4 w-4" />
            {host.session?.participantCount ?? 0} participant
            {host.session?.participantCount === 1 ? "" : "s"}
          </div>
          <QuestionTransition transitionKey={host.question.questionId ?? ""}>
            <QuestionCard question={host.question}>
              <AnswerGrid question={host.question} onSubmit={() => undefined} readOnly />
            </QuestionCard>
          </QuestionTransition>
        </div>
      );
    case "FINISHED":
      return (
        <EmptyState
          icon={Gamepad2}
          title="The session has finished"
          description="Results and the final leaderboard arrive with the next milestone."
          action={
            <Link to="/sessions">
              <Button variant="secondary">Back to sessions</Button>
            </Link>
          }
        />
      );
    default:
      return null;
  }
}
