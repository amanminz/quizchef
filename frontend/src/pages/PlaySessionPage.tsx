import { useParams } from "react-router-dom";
import { Card, CardContent } from "@/components/common/Card";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { SectionHeader } from "@/components/common/SectionHeader";
import { Spinner } from "@/components/common/Spinner";
import { AnswerGrid } from "@/features/gameplay/components/AnswerGrid";
import { AnswerRevealCard } from "@/features/gameplay/components/AnswerRevealCard";
import { CountdownOverlay } from "@/features/gameplay/components/CountdownOverlay";
import { FinalResultsPendingScreen } from "@/features/gameplay/components/FinalResultsPendingScreen";
import { GameConnectionBanner } from "@/features/gameplay/components/GameConnectionBanner";
import { FinalPlacementCard } from "@/features/gameplay/components/FinalPlacementCard";
import { PersonalAnswerFeedback } from "@/features/gameplay/components/PersonalAnswerFeedback";
import { PlayAgainCard } from "@/features/gameplay/components/PlayAgainCard";
import {
  JoinSessionForm,
  type JoinSessionFormValues
} from "@/features/gameplay/components/JoinSessionForm";
import { QuestionCard } from "@/features/gameplay/components/QuestionCard";
import { QuestionPreviewNotice } from "@/features/gameplay/components/QuestionPreviewNotice";
import { QuestionSkeleton } from "@/features/gameplay/components/QuestionSkeleton";
import { QuestionTransition } from "@/features/gameplay/components/QuestionTransition";
import { SubmissionStatus } from "@/features/gameplay/components/SubmissionStatus";
import { WaitingOverlay } from "@/features/gameplay/components/WaitingOverlay";
import { useCountdown } from "@/features/gameplay/hooks/useCountdown";
import { isEndpointMissing } from "@/features/gameplay/hooks/useFinalPlacement";
import { usePlayerGameplay } from "@/features/gameplay/hooks/usePlayerGameplay";
import { isLastQuestion } from "@/features/gameplay/isLastQuestion";
import { motivationFor } from "@/features/gameplay/motivation";
import { verdictFor } from "@/features/gameplay/verdict";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";

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
  // The quiz's identity comes from the participant-safe session summary —
  // authoritative data, never local navigation state, so it survives
  // refreshes and reconnects on every screen of the journey.
  const quizTitle = player.session?.quizTitle;
  useDocumentTitle(quizTitle);

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
      {quizTitle && (
        <h1 className="mx-auto mb-4 max-w-prose break-words text-center text-xl font-bold leading-snug tracking-tight sm:text-2xl">
          {quizTitle}
        </h1>
      )}
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
    case "QUESTION_PREVIEW":
      if (!player.question) {
        return <QuestionSkeleton />;
      }
      return (
        <QuestionTransition transitionKey={player.question.questionId ?? ""}>
          <QuestionCard question={player.question} preferredLanguage={player.preferredLanguage}>
            <QuestionPreviewNotice />
          </QuestionCard>
        </QuestionTransition>
      );
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
    case "ANSWER_REVEALED": {
      if (!player.question) {
        return <QuestionSkeleton />;
      }
      // On the quiz's last question the personal read is held for the
      // ceremony, so there is no score to show and the closing line
      // replaces the usual encouragement — the answer itself still
      // renders, which is content, not standings.
      const finalQuestion = isLastQuestion(player.question);
      const verdict = verdictFor(player.submittedOptionIds, player.question.correctOptionIds);
      return (
        <div className="flex flex-col gap-4">
          <PersonalAnswerFeedback
            verdict={verdict}
            quizComplete={finalQuestion}
            pointsEarned={finalQuestion ? undefined : player.personalResult?.pointsEarned}
            totalScore={finalQuestion ? undefined : player.personalResult?.score}
            message={motivationFor({
              sessionId: player.session?.sessionId,
              participantId: player.participantId,
              questionNumber: player.question.questionNumber,
              outcome: finalQuestion ? "final" : verdict,
              language: player.preferredLanguage
            })}
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
    }
    case "LEADERBOARD": {
      // The host is projecting the animated Top 5. This device shows the
      // player their own result and nothing about anyone's position —
      // not their rank, not their movement, not who is near them. The
      // data to show any of that is not on the wire (see the server's
      // ParticipantResultView), so there is nothing here to leak.
      if (isLastQuestion(player.question)) {
        // The last question's standings belong to the ceremony.
        return <FinalResultsPendingScreen language={player.preferredLanguage} />;
      }
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
      const verdict = verdictFor(player.submittedOptionIds, player.question?.correctOptionIds);
      return (
        <PersonalAnswerFeedback
          verdict={verdict}
          pointsEarned={player.personalResult.pointsEarned}
          totalScore={player.personalResult.score}
          message={motivationFor({
            sessionId: player.session?.sessionId,
            participantId: player.participantId,
            questionNumber: player.question?.questionNumber,
            outcome: verdict,
            language: player.preferredLanguage
          })}
        />
      );
    }
    case "FINAL_RESULTS_PENDING":
      return <FinalResultsPendingScreen language={player.preferredLanguage} />;
    case "FINISHED":
      // A backend without this endpoint yet (a staggered deploy: the
      // frontend and backend are separate Railway services and do not land
      // together). "Results aren't out yet" is both the truthful reading
      // and the same screen the participant was already on, so the
      // transition is invisible rather than an error page.
      if (isEndpointMissing(player.finalPlacementError)) {
        return <FinalResultsPendingScreen language={player.preferredLanguage} />;
      }
      if (player.finalPlacementError != null) {
        return (
          <ErrorPanel
            title="Your result is unavailable"
            error={player.finalPlacementError}
            onRetry={() => void player.refetchFinalPlacement()}
          />
        );
      }
      if (!player.finalPlacement) {
        return (
          <div className="flex justify-center py-16">
            <Spinner size="lg" className="text-primary" />
          </div>
        );
      }
      return (
        <div className="flex flex-col gap-6">
          <FinalPlacementCard
            placement={player.finalPlacement}
            message={motivationFor({
              sessionId: player.session?.sessionId,
              participantId: player.participantId,
              questionNumber: player.finalPlacement.totalQuestions,
              outcome: "final",
              language: player.preferredLanguage
            })}
          />
          <PlayAgainCard role="participant" />
        </div>
      );
    default:
      return null;
  }
}
