import { Gamepad2, Users } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { Button } from "@/components/common/Button";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { Spinner } from "@/components/common/Spinner";
import { WorkflowHeader } from "@/components/common/WorkflowHeader";
import { AnswerProgressBadge } from "@/features/gameplay/components/AnswerProgressBadge";
import { CompletionBanner } from "@/features/gameplay/components/CompletionBanner";
import { CountdownOverlay } from "@/features/gameplay/components/CountdownOverlay";
import { FinalStatistics } from "@/features/gameplay/components/FinalStatistics";
import { GameConnectionBanner } from "@/features/gameplay/components/GameConnectionBanner";
import { HostBilingualQuestion } from "@/features/gameplay/components/HostBilingualQuestion";
import { LeaderboardTable } from "@/features/gameplay/components/LeaderboardTable";
import { PlayAgainCard } from "@/features/gameplay/components/PlayAgainCard";
import { PodiumReveal } from "@/features/gameplay/components/PodiumReveal";
import { QuestionSkeleton } from "@/features/gameplay/components/QuestionSkeleton";
import { QuestionTransition } from "@/features/gameplay/components/QuestionTransition";
import { SessionSummaryCard } from "@/features/gameplay/components/SessionSummaryCard";
import { useGameHost } from "@/features/gameplay/hooks/useGameHost";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { PresentationToggle } from "@/features/sessions/components/PresentationToggle";
import { SessionStatusBadge } from "@/features/sessions/components/SessionStatusBadge";
import { usePresentationMode } from "@/features/sessions/hooks/usePresentationMode";
import { useQuizTitle } from "@/features/sessions/hooks/useQuizTitle";

/**
 * The host's gameplay screen, now spanning the full lifecycle: question →
 * reveal → leaderboard → next → … → final results. Host never answers —
 * options render read-only so the host sees what participants see — and
 * the one action, `nextStep`, issues exactly the host command the current
 * phase calls for (see `useGameHost`); never optimistic, its result is
 * always what the next render reflects.
 */
export function SessionLivePage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const host = useGameHost(sessionId);
  const fallbackTitle = useQuizTitle(host.session?.publishedQuizVersionId);
  const quizTitle = host.session?.quizTitle ?? fallbackTitle;
  const presentation = usePresentationMode();
  useDocumentTitle(quizTitle);

  // Early-reveal emphasis: when everyone eligible has answered, the next
  // valid transition (Close Question → Reveal Answer) pulses. Never
  // auto-fired — the host stays in control, and the server's FSM still
  // guarantees exactly one transition.
  const emphasizeAdvance =
    host.allAnswered && (host.phase === "QUESTION_OPEN" || host.phase === "WAITING");

  const advanceAction = host.canAdvance ? (
    <Button
      onClick={() => void host.nextStep()}
      isLoading={host.isAdvancing}
      className={
        emphasizeAdvance ? "animate-pulse ring-2 ring-success ring-offset-2" : undefined
      }
    >
      {host.nextStepLabel}
    </Button>
  ) : undefined;

  return (
    <PageContainer className={presentation.active ? "max-w-none px-8" : undefined}>
      {presentation.active ? (
        <div className="mb-4 flex items-start justify-between gap-4">
          <h1 className="text-2xl font-bold tracking-tight">{quizTitle ?? "Live session"}</h1>
          <div className="flex items-center gap-2">
            {advanceAction}
            <PresentationToggle presentation={presentation} />
          </div>
        </div>
      ) : (
        <WorkflowHeader
          title={quizTitle ?? "Live session"}
          backHref="/sessions"
          backLabel="Back to sessions"
          status={<SessionStatusBadge state={host.session?.state} />}
          actions={
            <div className="flex items-center gap-2">
              {advanceAction}
              <PresentationToggle presentation={presentation} />
            </div>
          }
        />
      )}

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
        <HostGameplayBody host={host} quizTitle={quizTitle} />
      )}
    </PageContainer>
  );
}

function ParticipantCount({ count }: { count: number }) {
  return (
    <div className="flex items-center gap-2 text-sm text-muted-foreground">
      <Users aria-hidden className="h-4 w-4" />
      {count} participant{count === 1 ? "" : "s"}
    </div>
  );
}

function HostGameplayBody({
  host,
  quizTitle
}: {
  host: ReturnType<typeof useGameHost>;
  quizTitle: string | undefined;
}) {
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
          <ParticipantCount count={host.session?.participantCount ?? 0} />
          <QuestionTransition transitionKey={host.question.questionId ?? ""}>
            <HostBilingualQuestion
              question={host.question}
              headerExtra={
                <AnswerProgressBadge progress={host.answerProgress} emphasized={host.allAnswered} />
              }
            />
          </QuestionTransition>
        </div>
      );
    case "ANSWER_REVEALED":
      if (!host.question) {
        return <QuestionSkeleton />;
      }
      return (
        <div className="flex flex-col gap-4">
          <ParticipantCount count={host.session?.participantCount ?? 0} />
          <HostBilingualQuestion question={host.question} revealed />
        </div>
      );
    case "LEADERBOARD":
      if (host.resultsError != null) {
        return (
          <ErrorPanel
            title="Leaderboard unavailable"
            error={host.resultsError}
            onRetry={() => void host.refetchResults()}
          />
        );
      }
      if (!host.results) {
        return (
          <div className="flex justify-center py-16">
            <Spinner size="lg" className="text-primary" />
          </div>
        );
      }
      return (
        <div className="flex flex-col gap-4">
          <ParticipantCount count={host.session?.participantCount ?? 0} />
          <LeaderboardTable entries={host.results.entries ?? []} caption="Current standings" />
        </div>
      );
    case "FINISHED":
      if (host.resultsError != null) {
        return (
          <ErrorPanel
            title="Results unavailable"
            error={host.resultsError}
            onRetry={() => void host.refetchResults()}
          />
        );
      }
      if (!host.results) {
        return (
          <div className="flex justify-center py-16">
            <Spinner size="lg" className="text-primary" />
          </div>
        );
      }
      return (
        <div className="flex flex-col gap-6">
          <CompletionBanner />
          <PodiumReveal
            sessionId={host.session?.sessionId ?? ""}
            entries={host.results.entries ?? []}
            footer={
              <div className="flex flex-col gap-6">
                <FinalStatistics results={host.results} />
                <SessionSummaryCard
                  quizTitle={quizTitle}
                  results={host.results}
                  actions={<PlayAgainCard role="host" />}
                />
              </div>
            }
          />
        </div>
      );
    default:
      return null;
  }
}
