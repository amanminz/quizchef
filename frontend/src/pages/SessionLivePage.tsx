import { Gamepad2, PartyPopper, Users } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { Button } from "@/components/common/Button";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { Spinner } from "@/components/common/Spinner";
import { WorkflowHeader } from "@/components/common/WorkflowHeader";
import { AnimatedTopFiveLeaderboard } from "@/features/gameplay/components/AnimatedTopFiveLeaderboard";
import { AnswerProgressBadge } from "@/features/gameplay/components/AnswerProgressBadge";
import { CompletionBanner } from "@/features/gameplay/components/CompletionBanner";
import { CountdownOverlay } from "@/features/gameplay/components/CountdownOverlay";
import { FinalStandingsTable } from "@/features/gameplay/components/FinalStandingsTable";
import { FinalStatistics } from "@/features/gameplay/components/FinalStatistics";
import { GameConnectionBanner } from "@/features/gameplay/components/GameConnectionBanner";
import { HostBilingualQuestion } from "@/features/gameplay/components/HostBilingualQuestion";
import { LeaderboardTable } from "@/features/gameplay/components/LeaderboardTable";
import { PlayAgainCard } from "@/features/gameplay/components/PlayAgainCard";
import { PodiumReveal } from "@/features/gameplay/components/PodiumReveal";
import { QuestionSkeleton } from "@/features/gameplay/components/QuestionSkeleton";
import { QuestionTransition } from "@/features/gameplay/components/QuestionTransition";
import { ReleaseResultsButton } from "@/features/gameplay/components/ReleaseResultsButton";
import { SessionSummaryCard } from "@/features/gameplay/components/SessionSummaryCard";
import { useGameHost } from "@/features/gameplay/hooks/useGameHost";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { PresentationToggle } from "@/features/sessions/components/PresentationToggle";
import { SessionStatusBadge } from "@/features/sessions/components/SessionStatusBadge";
import { usePresentationMode } from "@/features/sessions/hooks/usePresentationMode";
import { useQuizTitle } from "@/features/sessions/hooks/useQuizTitle";
import { cn } from "@/utils/cn";

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
    <PageContainer
      className={
        // Viewport containment (goal: fit a projector screen with no
        // scrolling): a fixed-height flex column, never taller than the
        // viewport. `overflow-hidden` is a safety net, not the fitting
        // mechanism — the actual fit comes from the compact status row,
        // the dropped progress bar, and clamp()-based text sizing below.
        presentation.active
          ? "flex h-dvh max-w-none flex-col overflow-hidden px-6 py-3 sm:px-8"
          : undefined
      }
    >
      {presentation.active ? (
        <div className="mb-2 flex shrink-0 items-start justify-between gap-4">
          <h1 className="text-xl font-bold tracking-tight sm:text-2xl">
            {quizTitle ?? "Live session"}
          </h1>
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

      <div className={presentation.active ? "shrink-0" : undefined}>
        <GameConnectionBanner status={host.connectionStatus} />
      </div>
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
        <div className={presentation.active ? "mb-2 shrink-0" : "mb-6"}>
          <ErrorPanel title="Could not advance the game" error={host.nextStepError} />
        </div>
      )}

      {!host.isLoadingSession && host.sessionError == null && (
        <div className={presentation.active ? "min-h-0 flex-1 overflow-hidden" : undefined}>
          <HostGameplayBody host={host} quizTitle={quizTitle} presentation={presentation} />
        </div>
      )}
    </PageContainer>
  );
}

/**
 * The host's own between-the-final-reveal-and-the-podium state. Says what
 * happens next and shows no standings whatsoever — the finishing order is
 * the ceremony's to reveal, and this screen is on a projector.
 */
function FinalCeremonyPending() {
  return (
    <div
      role="status"
      className="flex flex-col items-center gap-3 rounded-lg border border-dashed px-6 py-16 text-center"
    >
      <PartyPopper aria-hidden className="h-8 w-8 text-primary" />
      <p className="text-xl font-bold">That was the last question</p>
      <p className="max-w-sm text-sm text-muted-foreground">
        Finish the quiz to run the winners&rsquo; ceremony.
      </p>
    </div>
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
  quizTitle,
  presentation
}: {
  host: ReturnType<typeof useGameHost>;
  quizTitle: string | undefined;
  presentation: ReturnType<typeof usePresentationMode>;
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
    case "QUESTION_PREVIEW":
      if (!host.question) {
        return <QuestionSkeleton />;
      }
      return (
        <div
          className={cn(
            "flex flex-col gap-4",
            presentation.active && "h-full min-h-0 gap-2 overflow-hidden"
          )}
        >
          {!presentation.active && (
            <ParticipantCount count={host.session?.participantCount ?? 0} />
          )}
          <QuestionTransition
            transitionKey={host.question.questionId ?? ""}
            className={presentation.active ? "flex min-h-0 flex-1 flex-col" : undefined}
          >
            <HostBilingualQuestion
              question={host.question}
              previewing
              presentationActive={presentation.active}
            />
          </QuestionTransition>
        </div>
      );
    case "QUESTION_OPEN":
    case "WAITING":
      if (!host.question) {
        return <QuestionSkeleton />;
      }
      return (
        <div
          className={cn(
            "flex flex-col gap-4",
            presentation.active && "h-full min-h-0 gap-2 overflow-hidden"
          )}
        >
          {!presentation.active && (
            <ParticipantCount count={host.session?.participantCount ?? 0} />
          )}
          <QuestionTransition
            transitionKey={host.question.questionId ?? ""}
            className={presentation.active ? "flex min-h-0 flex-1 flex-col" : undefined}
          >
            <HostBilingualQuestion
              question={host.question}
              presentationActive={presentation.active}
              answerProgress={host.answerProgress}
              emphasized={host.allAnswered}
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
        <div
          className={cn(
            "flex flex-col gap-4",
            presentation.active && "h-full min-h-0 gap-2 overflow-hidden"
          )}
        >
          {!presentation.active && (
            <ParticipantCount count={host.session?.participantCount ?? 0} />
          )}
          <div className={presentation.active ? "min-h-0 flex-1 overflow-hidden" : undefined}>
            <HostBilingualQuestion
              question={host.question}
              revealed
              distribution={host.answerDistribution}
              presentationActive={presentation.active}
            />
          </div>
        </div>
      );
    case "LEADERBOARD":
      // The quiz's last question has no leaderboard step — the server
      // refuses to enter one, so this is unreachable in a healthy game.
      // It is still handled explicitly rather than falling through to a
      // board: a stale tab or a reconnect that lands here must go to the
      // ceremony, never flash the finishing order onto the projector
      // ahead of it.
      if (host.isLastQuestion) {
        return <FinalCeremonyPending />;
      }
      if (host.topFiveTransition) {
        return (
          <AnimatedTopFiveLeaderboard
            transition={host.topFiveTransition}
            presentationActive={presentation.active}
            onComplete={host.markLeaderboardAnimationComplete}
          />
        );
      }
      if (host.isLoadingTopFive) {
        return (
          <div className="flex justify-center py-16">
            <Spinner size="lg" className="text-primary" />
          </div>
        );
      }
      // The projection failed. The standings are the host's own read and
      // still trustworthy, so fall back to the plain table rather than
      // stranding the room on an error — the animation is presentation,
      // not the data.
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
    case "FINAL_RESULTS_PENDING":
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
            exactRankRevealCount={host.results.exactRankRevealCount ?? 0}
            footer={
              <div className="flex flex-col gap-6">
                <ReleaseResultsButton
                  released={host.finalResultsReleased}
                  isReleasing={host.isReleasingFinalResults}
                  error={host.releaseFinalResultsError}
                  onRelease={() => void host.releaseFinalResults()}
                />
                {/* The captured history, not the live projection: the same
                    numbers today, and still the same after a scoring rule
                    changes. */}
                {host.finalStandings && (
                  <FinalStandingsTable standings={host.finalStandings} />
                )}
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
