import { Lightbulb } from "lucide-react";
import { CorrectAnswerBadge } from "@/features/gameplay/components/CorrectAnswerBadge";
import { resolveLocalization } from "@/features/gameplay/resolveLocalization";
import type { CurrentQuestionResponse } from "@/types/api";
import { cn } from "@/utils/cn";

export interface AnswerRevealCardProps {
  /** The revealed question — `correctOptionIds` and `explanation` are present. */
  question: CurrentQuestionResponse;
  preferredLanguage?: string;
  /** The viewer's own accepted submission; omitted for the host. */
  submittedOptionIds?: string[];
}

/**
 * The reveal: every option, with the server-revealed correct ones badged
 * and the viewer's own selection marked. Entirely server-driven — which
 * options are correct comes from `correctOptionIds` (revealed by the
 * backend at this exact phase, never sooner), and the explanation is the
 * author's own text, phase-gated server-side the same way.
 */
export function AnswerRevealCard({
  question,
  preferredLanguage,
  submittedOptionIds
}: AnswerRevealCardProps) {
  const localization = resolveLocalization(question, preferredLanguage);
  const correct = new Set(question.correctOptionIds ?? []);
  const submitted = new Set(submittedOptionIds ?? []);
  const options = [...(question.options ?? [])].sort(
    (a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)
  );

  return (
    <div className="flex flex-col gap-3">
      <ul className="grid gap-2 sm:grid-cols-2">
        {options.map((option) => {
          const id = option.optionId ?? "";
          const isCorrect = correct.has(id);
          const isChosen = submitted.has(id);
          const text =
            localization?.optionTexts?.find((entry) => entry.optionId === id)?.text ?? "";
          return (
            <li
              key={id}
              className={cn(
                "flex flex-col gap-1.5 rounded-md border px-4 py-3 text-sm font-medium",
                isCorrect ? "border-success bg-success/5" : "opacity-70"
              )}
            >
              <span>{text}</span>
              <span className="flex flex-wrap items-center gap-2">
                {isCorrect && <CorrectAnswerBadge />}
                {isChosen && (
                  <span className="inline-flex items-center rounded-full bg-primary/15 px-2 py-0.5 text-xs font-semibold text-primary">
                    Your answer
                  </span>
                )}
              </span>
            </li>
          );
        })}
      </ul>

      {localization?.explanation && (
        <div className="flex items-start gap-3 rounded-md border border-primary/30 bg-primary/5 px-4 py-3 text-sm">
          <Lightbulb aria-hidden className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
          <p>{localization.explanation}</p>
        </div>
      )}
    </div>
  );
}
