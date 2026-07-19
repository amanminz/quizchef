import { Lightbulb } from "lucide-react";
import { Card, CardContent } from "@/components/common/Card";
import { CorrectAnswerBadge } from "@/features/gameplay/components/CorrectAnswerBadge";
import { QuestionHeader } from "@/features/gameplay/components/QuestionHeader";
import type { CurrentQuestionResponse, PlayableLocalizationDto } from "@/types/api";
import { cn } from "@/utils/cn";

export interface HostBilingualQuestionProps {
  question: CurrentQuestionResponse;
  /** Reveal view: correct options highlighted, explanations shown. */
  revealed?: boolean;
  /** Extra header content (answer progress, participant count). */
  headerExtra?: React.ReactNode;
}

const LANGUAGE_ENGLISH_NAMES: Record<string, string> = {
  en: "English",
  hi: "Hindi"
};

function englishName(language: string): string {
  return LANGUAGE_ENGLISH_NAMES[language] ?? language;
}

/**
 * The host's projected question: English and Hindi rendered together
 * whenever both localizations exist, sized and spaced for a projector —
 * large type, high contrast, aligned option rows, no non-essential
 * metadata. When the second language is missing, the default renders
 * once with a subtle notice. On reveal, the correct options and both
 * languages' explanations show.
 */
export function HostBilingualQuestion({
  question,
  revealed = false,
  headerExtra
}: HostBilingualQuestionProps) {
  const localizations = question.localizations ?? [];
  const primary =
    localizations.find((entry) => entry.languageCode === question.defaultLanguage) ??
    localizations[0];
  // The projection pairs the default with the other live-event language.
  const secondaryLanguage = primary?.languageCode === "hi" ? "en" : "hi";
  const secondary = localizations.find((entry) => entry.languageCode === secondaryLanguage);

  const options = [...(question.options ?? [])].sort(
    (a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)
  );
  const correct = new Set(question.correctOptionIds ?? []);

  const textOf = (localization: PlayableLocalizationDto | undefined, optionId: string) =>
    localization?.optionTexts?.find((entry) => entry.optionId === optionId)?.text;

  return (
    <Card>
      <CardContent className="flex flex-col gap-6 p-6 sm:p-8">
        <QuestionHeader
          number={question.questionNumber ?? 0}
          total={question.totalQuestions ?? 0}
          endsAt={question.phase === "QUESTION_OPEN" ? question.endsAt : null}
        />
        {headerExtra}

        <div className="flex flex-col gap-2">
          <p className="text-2xl font-bold leading-snug sm:text-3xl lg:text-4xl">
            {primary?.prompt}
          </p>
          {secondary && (
            <p lang={secondaryLanguage} className="text-xl font-semibold leading-snug text-foreground/90 sm:text-2xl lg:text-3xl">
              {secondary.prompt}
            </p>
          )}
          {!secondary && (
            <p className="text-sm text-muted-foreground">
              {englishName(secondaryLanguage)} translation unavailable for this question.
            </p>
          )}
        </div>

        <ol className="flex flex-col gap-3">
          {options.map((option, index) => {
            const id = option.optionId ?? "";
            const isCorrect = revealed && correct.has(id);
            return (
              <li
                key={id}
                className={cn(
                  "flex items-start gap-4 rounded-lg border-2 px-5 py-4",
                  isCorrect ? "border-success bg-success/10" : "border-border",
                  revealed && !isCorrect && "opacity-60"
                )}
              >
                <span
                  aria-hidden
                  className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-muted text-lg font-bold"
                >
                  {String.fromCharCode(65 + index)}
                </span>
                <span className="flex min-w-0 flex-1 flex-col gap-1">
                  <span className="text-lg font-semibold leading-snug sm:text-xl lg:text-2xl">
                    {textOf(primary, id)}
                  </span>
                  {secondary && (
                    <span lang={secondaryLanguage} className="text-base font-medium leading-snug text-foreground/85 sm:text-lg lg:text-xl">
                      {textOf(secondary, id)}
                    </span>
                  )}
                </span>
                {isCorrect && (
                  <span className="mt-1 shrink-0">
                    <CorrectAnswerBadge />
                  </span>
                )}
              </li>
            );
          })}
        </ol>

        {revealed && (primary?.explanation || secondary?.explanation) && (
          <div className="flex items-start gap-3 rounded-md border border-primary/30 bg-primary/5 px-5 py-4">
            <Lightbulb aria-hidden className="mt-1 h-5 w-5 shrink-0 text-primary" />
            <div className="flex flex-col gap-2">
              {primary?.explanation && (
                <p className="text-base leading-relaxed sm:text-lg">{primary.explanation}</p>
              )}
              {secondary?.explanation && (
                <p lang={secondaryLanguage} className="text-base leading-relaxed text-foreground/90 sm:text-lg">
                  {secondary.explanation}
                </p>
              )}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
