import { useQuiz } from "@/features/quizzes/hooks/useQuiz";

/**
 * Resolves the quiz title behind a session's published quiz version id —
 * which is the quiz's own id today (QuizPublicationQuery), so this rides
 * the same cached quiz query the authoring pages use. Returns undefined
 * while loading or when the quiz is not readable.
 */
export function useQuizTitle(publishedQuizVersionId: string | undefined): string | undefined {
  const { data: quiz } = useQuiz(publishedQuizVersionId);
  return (
    quiz?.localizations?.find(
      (localization) => localization.languageCode === quiz.defaultLanguage
    )?.title ?? quiz?.localizations?.[0]?.title
  );
}
