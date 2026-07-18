import { useParams } from "react-router-dom";
import { Card, CardContent } from "@/components/common/Card";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { SectionHeader } from "@/components/common/SectionHeader";
import { Spinner } from "@/components/common/Spinner";
import { AnswerGrid } from "@/features/gameplay/components/AnswerGrid";
import { AnswerRevealCard } from "@/features/gameplay/components/AnswerRevealCard";
import { CompletionBanner } from "@/features/gameplay/components/CompletionBanner";
import { CountdownOverlay } from "@/features/gameplay/components/CountdownOverlay";
import { GameConnectionBanner } from "@/features/gameplay/components/GameConnectionBanner";
import { PersonalRankCard } from "@/features/gameplay/components/PersonalRankCard";
import { PlayAgainCard } from "@/features/gameplay/components/PlayAgainCard";
import { QuestionResult } from "@/features/gameplay/components/QuestionResult";
import {
  JoinSessionForm,
  type JoinSessionFormValues
} from "@/features/gameplay/components/JoinSessionForm";
import { QuestionCard } from "@/features/gameplay/components/QuestionCard";
import { QuestionSkeleton } from "@/features/gameplay/components/QuestionSkeleton";
import { QuestionTransition } from "@/features/gameplay/components/QuestionTransition";
import { SubmissionStatus } from "@/features/gameplay/components/SubmissionStatus";
import { WaitingOverlay } from "@/features/gameplay/components/WaitingOverlay";
import { useCountdown } from "@/features/gameplay/hooks/useCountdown";
import { usePlayerGameplay } from "@/features/gameplay/hooks/usePlayerGameplay";
import { verdictFor } from "@/features/gameplay/verdict";

/**
 * The participant's gameplay screen. Everything renders off one FSM phase
 * (`useGameplayState`, via `usePlayerGameplay`) — no component here infers
 * state from scattered conditionals. Reconnect always runs before this
 * renders live content, so a refresh mid-question restores the submitted
 * answer instead of re-showing an empty grid (see `usePlayerGameplay`).
 */
export function PlaySessionPage() {
  const { pin = "" } = useParams<{ pin: string }>();
  const player = usePlayerGameplay(pin);

  const onJoin = async (values: JoinSessionFormValues) => {
    await player.join({
      displayName: values.displayName,
      preferredLanguage: values.preferredLanguage
    });
  };

  if (!player.hasJoined) {
    return (
      <PageContainer className="max-w-md py-16">
        <SectionHeader title="Join a game" description="Enter your name to play." />
        <Card>
          <CardContent className="pt-6">
            <JoinSessionForm
              fixedPin={pin}
              onSubmit={onJoin}
              isSubmitting={player.isJoining}
              error={player.joinError}
            />
          </CardContent>
        </Card>
      </PageContainer>
    );
  }

  return (
    <PageContainer className="max-w-2xl py-8">
      <GameConnectionBanner status={player.connectionStatus} />
      <div aria-live="polite" role="status" className="sr-only">
        {player.announcement}
      </div>

      {(player.isReconnecting || player.isLoadingSession) && (
        <div className="flex justify-center py-16">
          <Spinner size="lg" className="text-primary" />
        </div>
      )}

      {player.reconnectError != null && (
        <ErrorPanel
          title="Could not reconnect"
          error={player.reconnectError}
          onRetry={player.retryReconnect}
        />
      )}

      {!player.isReconnecting && !player.isLoadingSession && player.reconnectError == null && (
        <PlayerGameplayBody player={player} />
      )}
    </PageContainer>
  );
}

function PlayerGameplayBody({ player }: { player: ReturnType<typeof usePlayerGameplay> }) {
  const { isExpired } = useCountdown(
    player.question?.phase === "QUESTION_OPEN" ? player.question.endsAt : null
  );

  switch (player.phase) {
    case "LOBBY":
      return (
        <div className="rounded-lg border border-dashed px-6 py-16 text-center text-sm text-muted-foreground">
          Waiting for the host to start the session…
        </div>
      );
    case "COUNTDOWN":
      return <CountdownOverlay />;
    case "QUESTION_OPEN":
      if (!player.question) {
        return <QuestionSkeleton />;
      }
      return (
        <QuestionTransition transitionKey={player.question.questionId ?? ""}>
          <QuestionCard question={player.question} preferredLanguage={player.preferredLanguage}>
            {player.hasSubmitted ? (
              <SubmissionStatus />
            ) : (
              <AnswerGrid
                question={player.question}
                preferredLanguage={player.preferredLanguage}
                disabled={isExpired}
                onSubmit={player.submit}
                isSubmitting={player.isSubmitting}
              />
            )}
          </QuestionCard>
        </QuestionTransition>
      );
    case "WAITING":
      return <WaitingOverlay />;
    case "ANSWER_REVEALED":
      if (!player.question) {
        return <QuestionSkeleton />;
      }
      return (
        <div className="flex flex-col gap-4">
          <QuestionResult
            verdict={verdictFor(player.submittedOptionIds, player.question.correctOptionIds)}
          />
          <QuestionCard question={player.question} preferredLanguage={player.preferredLanguage}>
            <AnswerRevealCard
              question={player.question}
              preferredLanguage={player.preferredLanguage}
              submittedOptionIds={player.submittedOptionIds}
            />
          </QuestionCard>
        </div>
      );
    case "LEADERBOARD":
      // Participant privacy: only the player's own rank and score render
      // here — the shared leaderboard belongs to the host's projected
      // screen, and this device never even fetches it.
      if (player.personalResultError != null) {
        return (
          <ErrorPanel
            title="Your result is unavailable"
            error={player.personalResultError}
            onRetry={() => void player.refetchPersonalResult()}
          />
        );
      }
      if (!player.personalResult) {
        return (
          <div className="flex justify-center py-16">
            <Spinner size="lg" className="text-primary" />
          </div>
        );
      }
      return (
        <PersonalRankCard
          result={player.personalResult}
          scoreDelta={player.scoreDelta}
          rankDelta={player.rankDelta}
        />
      );
    case "FINISHED":
      if (player.personalResultError != null) {
        return (
          <ErrorPanel
            title="Your result is unavailable"
            error={player.personalResultError}
            onRetry={() => void player.refetchPersonalResult()}
          />
        );
      }
      if (!player.personalResult) {
        return (
          <div className="flex justify-center py-16">
            <Spinner size="lg" className="text-primary" />
          </div>
        );
      }
      return (
        <div className="flex flex-col gap-6">
          <CompletionBanner />
          <PersonalRankCard result={player.personalResult} variant="final" />
          <PlayAgainCard role="participant" />
        </div>
      );
    default:
      return null;
  }
}
