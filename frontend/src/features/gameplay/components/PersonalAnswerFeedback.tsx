import { CheckCircle2, Info, XCircle } from "lucide-react";
import type { AnswerVerdict } from "@/features/gameplay/verdict";
import { cn } from "@/utils/cn";

export interface PersonalAnswerFeedbackProps {
  verdict: AnswerVerdict;
  /** What this question awarded them — the server's number, not a diff. */
  pointsEarned?: number;
  /** Their running total, once the server will tell us. */
  totalScore?: number;
  /** The line from the catalogue, already resolved to their language. */
  message: string;
  /**
   * The quiz has no more questions. Says so plainly rather than leaving the
   * player on a screen that looks like every other reveal: without it the
   * only signal is a motivational line, and a player who reads "watch the
   * main screen" has no way to tell whether one more question is coming.
   */
  quizComplete?: boolean;
}

const HEADLINE: Record<AnswerVerdict, { icon: typeof CheckCircle2; text: string; tone: string }> = {
  correct: {
    icon: CheckCircle2,
    text: "Correct!",
    tone: "border-success/30 bg-success/5 text-success"
  },
  // Deliberately "Not quite this time" rather than a bare "Wrong": the
  // difference costs nothing and is the whole point of this screen.
  incorrect: {
    icon: XCircle,
    text: "Not quite this time",
    tone: "border-border bg-muted/40 text-foreground"
  },
  unanswered: {
    icon: Info,
    text: "Time's up",
    tone: "border-border bg-muted/40 text-muted-foreground"
  }
};

/**
 * Everything a participant is told after a question: whether they got it,
 * what it was worth, what they have altogether, and one line of
 * encouragement.
 *
 * What is deliberately absent is any sense of position. No rank, no
 * movement, no neighbours, no "you're doing better than N players" — the
 * standings are the host's screen and the ceremony's to reveal, and a
 * player who is quietly last should be able to read this card all evening
 * without learning that. The data to render one of those things does not
 * reach this device (see `ParticipantResultView` on the server), so this
 * is a component that could not show a rank if it tried.
 */
export function PersonalAnswerFeedback({
  verdict,
  pointsEarned,
  totalScore,
  message,
  quizComplete = false
}: PersonalAnswerFeedbackProps) {
  const { icon: Icon, text, tone } = HEADLINE[verdict];

  return (
    <section
      role="status"
      aria-label="Your result for this question"
      className={cn("flex flex-col items-center gap-3 rounded-lg border px-6 py-6 text-center", tone)}
    >
      <p className="flex items-center gap-2 text-lg font-bold">
        <Icon aria-hidden className="h-6 w-6 shrink-0" />
        {text}
      </p>

      {pointsEarned !== undefined && (
        <p className="font-mono text-3xl font-black leading-none tabular-nums">
          +{pointsEarned.toLocaleString()}
          <span className="ml-1 text-base font-semibold"> points</span>
        </p>
      )}

      {totalScore !== undefined && (
        <p className="text-sm text-muted-foreground">
          Total score:{" "}
          <span className="font-mono font-semibold text-foreground">
            {totalScore.toLocaleString()}
          </span>
        </p>
      )}

      {quizComplete && (
        <p className="text-base font-bold text-foreground">That was the last question</p>
      )}

      <p className="max-w-sm text-pretty text-sm font-medium text-foreground/80">{message}</p>
    </section>
  );
}
