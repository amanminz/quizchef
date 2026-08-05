import { BookOpen, Lightbulb } from "lucide-react";
import { Card, CardContent } from "@/components/common/Card";
import { CorrectAnswerBadge } from "@/features/gameplay/components/CorrectAnswerBadge";
import { QuestionHeader } from "@/features/gameplay/components/QuestionHeader";
import type {
  AnswerDistributionResponse,
  AnswerProgressResponse,
  CurrentQuestionResponse,
  PlayableLocalizationDto
} from "@/types/api";
import { cn } from "@/utils/cn";

export interface HostBilingualQuestionProps {
  question: CurrentQuestionResponse;
  /**
   * The reading period: the prompt renders, options never do — the
   * response genuinely carries none during `QUESTION_PREVIEW`, so this
   * isn't hiding anything, just not rendering an options block at all.
   */
  previewing?: boolean;
  /** Reveal view: correct options highlighted, explanations shown. */
  revealed?: boolean;
  /**
   * Per-option accepted-answer counts, shown alongside each option once
   * revealed. Counts are keyed by the stable option id (never translated
   * text), so English and Hindi rows always show the same number for the
   * same logical option.
   */
  distribution?: AnswerDistributionResponse;
  /**
   * Extra header content — rendered only outside Presentation Mode; in
   * Presentation Mode, `answerProgress` renders inline in the compact
   * status row instead (see `QuestionHeader`), so the same data never
   * renders twice.
   */
  headerExtra?: React.ReactNode;
  /** Presentation Mode swaps the compact timer for the projector-scale one. */
  presentationActive?: boolean;
  /** The backend's answered/eligible counts, for Presentation Mode's compact row. */
  answerProgress?: AnswerProgressResponse;
  /** Everyone eligible has answered — the moment worth emphasizing. */
  emphasized?: boolean;
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
 * languages' explanations show, alongside each option's accepted-answer
 * count and percentage when `distribution` is provided (for a
 * multiple-answer question these are option-*selection* counts, so their
 * sum may exceed the number of participants who answered).
 */
export function HostBilingualQuestion({
  question,
  previewing = false,
  revealed = false,
  distribution,
  headerExtra,
  presentationActive = false,
  answerProgress,
  emphasized = false
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

  const countFor = (optionId: string) =>
    distribution?.options?.find((entry) => entry.optionId === optionId);

  /**
   * Counts render for every option once revealed, including the ones nobody
   * picked — a visible `0 · 0%` is information, and a row that simply omits
   * its number reads as a rendering fault from the back of the room.
   */
  const showCounts = revealed && distribution !== undefined;

  // Presentation Mode's text sizes are clamp()-driven (viewport-aware,
  // never fixed), and deliberately smaller steps than the normal layout's
  // sm:/lg: classes — normal pages are untouched.
  const promptStyle = presentationActive
    ? { fontSize: "clamp(1.1rem, 2.6vw, 2.1rem)" }
    : undefined;
  const secondaryPromptStyle = presentationActive
    ? { fontSize: "clamp(1rem, 2.2vw, 1.75rem)" }
    : undefined;
  const optionPrimaryStyle = presentationActive
    ? { fontSize: "clamp(0.95rem, 1.8vw, 1.35rem)" }
    : undefined;
  const optionSecondaryStyle = presentationActive
    ? { fontSize: "clamp(0.85rem, 1.5vw, 1.1rem)" }
    : undefined;

  return (
    <Card className={presentationActive ? "flex h-full min-h-0 flex-col" : undefined}>
      <CardContent
        className={cn(
          "flex flex-col",
          presentationActive
            ? "min-h-0 flex-1 gap-2 overflow-hidden p-3 sm:gap-3 sm:p-4"
            : "gap-6 p-6 sm:p-8"
        )}
      >
        <QuestionHeader
          number={question.questionNumber ?? 0}
          total={question.totalQuestions ?? 0}
          endsAt={
            question.phase === "QUESTION_OPEN" || question.phase === "QUESTION_PREVIEW"
              ? question.endsAt
              : null
          }
          presentationActive={presentationActive}
          previewing={previewing}
          answerProgress={answerProgress}
          emphasized={emphasized}
        />
        {!presentationActive && headerExtra}

        <div
          className={cn(
            "flex flex-col gap-2",
            presentationActive && "lg:flex-row lg:items-baseline lg:gap-6"
          )}
        >
          <p
            className={cn(
              "font-bold leading-snug",
              presentationActive ? "lg:flex-1" : "text-2xl sm:text-3xl lg:text-4xl"
            )}
            style={promptStyle}
          >
            {primary?.prompt}
          </p>
          {secondary && (
            <p
              lang={secondaryLanguage}
              className={cn(
                "font-semibold leading-snug text-foreground/90",
                presentationActive ? "lg:flex-1" : "text-xl sm:text-2xl lg:text-3xl"
              )}
              style={secondaryPromptStyle}
            >
              {secondary.prompt}
            </p>
          )}
          {!secondary && (
            <p className="text-sm text-muted-foreground">
              {englishName(secondaryLanguage)} translation unavailable for this question.
            </p>
          )}
        </div>

        {previewing && (
          <div
            className={cn(
              "flex items-center gap-3 rounded-md border border-dashed text-muted-foreground",
              presentationActive ? "px-3 py-2" : "px-5 py-4"
            )}
          >
            <BookOpen aria-hidden className="h-5 w-5 shrink-0" />
            <p className="text-base font-medium sm:text-lg">
              Read the question — options will appear shortly
            </p>
          </div>
        )}

        {!previewing && (
          <ol
            className={cn(
              "flex flex-col",
              presentationActive ? "min-h-0 gap-1.5 overflow-hidden sm:gap-2" : "gap-3"
            )}
          >
            {options.map((option, index) => {
              const id = option.optionId ?? "";
              const isCorrect = revealed && correct.has(id);
              return (
                <li
                  key={id}
                  className={cn(
                    // A grid once the counts are showing: the label, count,
                    // and percentage columns get reserved width so the
                    // numbers stay put and stay aligned down the list, and
                    // a long bilingual option shortens itself rather than
                    // pushing them off the projector.
                    "grid items-start gap-4 rounded-lg border-2",
                    presentationActive ? "gap-2 px-3 py-1.5 sm:gap-3 sm:py-2" : "px-5 py-4",
                    isCorrect ? "border-success bg-success/10" : "border-border",
                    revealed && !isCorrect && "opacity-60"
                  )}
                  style={{
                    gridTemplateColumns: showCounts
                      ? "minmax(2.5rem, auto) minmax(0, 1fr) minmax(4rem, auto) minmax(5rem, auto)"
                      : "minmax(2.5rem, auto) minmax(0, 1fr)"
                  }}
                >
                  <span
                    aria-hidden
                    className={cn(
                      "mt-0.5 flex shrink-0 items-center justify-center rounded-full bg-muted font-bold",
                      presentationActive ? "h-6 w-6 text-sm sm:h-7 sm:w-7" : "h-9 w-9 text-lg"
                    )}
                  >
                    {String.fromCharCode(65 + index)}
                  </span>
                  <span className="flex min-w-0 flex-1 flex-col gap-1">
                    <span
                      className={cn(
                        "font-semibold leading-snug",
                        !presentationActive && "text-lg sm:text-xl lg:text-2xl"
                      )}
                      style={optionPrimaryStyle}
                    >
                      {textOf(primary, id)}
                    </span>
                    {secondary && (
                      <span
                        lang={secondaryLanguage}
                        className={cn(
                          "font-medium leading-snug text-foreground/85",
                          !presentationActive && "text-base sm:text-lg lg:text-xl"
                        )}
                        style={optionSecondaryStyle}
                      >
                        {textOf(secondary, id)}
                      </span>
                    )}
                  </span>
                  {showCounts && (
                    <>
                      {/* One count and one percentage for the option, not one
                          per language — the two localizations are the same
                          answer and share the server's single tally. */}
                      <span
                        className="whitespace-nowrap text-right font-mono font-bold tabular-nums"
                        style={{
                          fontSize: presentationActive
                            ? "clamp(1.45rem, 2.2vw, 2.6rem)"
                            : undefined
                        }}
                      >
                        {countFor(id)?.count ?? 0}
                      </span>
                      <span
                        className="whitespace-nowrap text-right font-mono font-bold tabular-nums text-muted-foreground"
                        style={{
                          fontSize: presentationActive
                            ? "clamp(1.45rem, 2.2vw, 2.6rem)"
                            : undefined
                        }}
                      >
                        {countFor(id)?.percentage ?? 0}%
                      </span>
                    </>
                  )}
                  {isCorrect && (
                    <span className="col-start-2 row-start-2 mt-1 justify-self-start">
                      <CorrectAnswerBadge />
                    </span>
                  )}
                </li>
              );
            })}
          </ol>
        )}

        {showCounts && (distribution.noAnswerCount ?? 0) > 0 && (
          <p
            className="font-mono font-bold tabular-nums text-muted-foreground"
            style={{
              fontSize: presentationActive ? "clamp(1.2rem, 1.8vw, 2rem)" : undefined
            }}
          >
            No answer: {distribution.noAnswerCount}
          </p>
        )}

        {revealed && (primary?.explanation || secondary?.explanation) && (
          <div
            className={cn(
              "flex items-start gap-3 rounded-md border border-primary/30 bg-primary/5",
              presentationActive ? "min-h-0 overflow-hidden px-3 py-2" : "px-5 py-4"
            )}
          >
            <Lightbulb aria-hidden className="mt-1 h-5 w-5 shrink-0 text-primary" />
            <div className="flex min-h-0 flex-col gap-2">
              {primary?.explanation && (
                <p
                  className={cn("leading-relaxed", !presentationActive && "text-base sm:text-lg")}
                  style={presentationActive ? { fontSize: "clamp(0.8rem, 1.4vw, 1.05rem)" } : undefined}
                >
                  {primary.explanation}
                </p>
              )}
              {secondary?.explanation && (
                <p
                  lang={secondaryLanguage}
                  className={cn(
                    "leading-relaxed text-foreground/90",
                    !presentationActive && "text-base sm:text-lg"
                  )}
                  style={
                    presentationActive ? { fontSize: "clamp(0.75rem, 1.2vw, 0.95rem)" } : undefined
                  }
                >
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
