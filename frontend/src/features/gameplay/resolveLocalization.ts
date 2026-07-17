import type { CurrentQuestionResponse, PlayableLocalizationDto } from "@/types/api";

/**
 * Picks the localization to render: the participant's preferred language
 * if the question has it, the question's own default language otherwise,
 * or simply the first authored localization as a last resort. Shared by
 * `QuestionBody` and `AnswerGrid` so both always agree on which language
 * is on screen.
 */
export function resolveLocalization(
  question: CurrentQuestionResponse,
  preferredLanguage: string | undefined
): PlayableLocalizationDto | undefined {
  const localizations = question.localizations ?? [];
  return (
    localizations.find((localization) => localization.languageCode === preferredLanguage) ??
    localizations.find((localization) => localization.languageCode === question.defaultLanguage) ??
    localizations[0]
  );
}
