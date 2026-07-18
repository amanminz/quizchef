import { useQueries } from "@tanstack/react-query";
import { questionApi } from "@/api/questionApi";
import { usePublishQuiz } from "@/features/quizzes/hooks/usePublishQuiz";
import { useQuiz } from "@/features/quizzes/hooks/useQuiz";
import { questionKeys } from "@/features/questions/queryKeys";
import type { QuestionResponse } from "@/types/api";

/**
 * Orchestrates the review-and-publish step: loads the quiz's attached
 * questions (in parallel, individually cached so the picker and the
 * review page share a cache), derives the summary the review page shows
 * (difficulty distribution, languages), and mirrors the server's own
 * publish precondition (RFC-003: every question must carry the quiz's
 * default language) as an up-front warning — a UX convenience only; the
 * server remains the authority and re-checks on publish regardless.
 */
export function useQuizPublishing(quizId: string) {
  const quizQuery = useQuiz(quizId);
  const questionIds = quizQuery.data?.questionIds ?? [];

  const questionQueries = useQueries({
    queries: questionIds.map((id) => ({
      queryKey: questionKeys.detail(id),
      queryFn: () => questionApi.getById(id),
      enabled: quizQuery.isSuccess
    }))
  });

  const questions = questionQueries
    .map((query) => query.data)
    .filter((question): question is QuestionResponse => question !== undefined);
  const isLoadingQuestions = questionQueries.some((query) => query.isPending);

  const difficultyDistribution: Record<string, number> = {};
  for (const question of questions) {
    const key = question.difficulty ?? "UNKNOWN";
    difficultyDistribution[key] = (difficultyDistribution[key] ?? 0) + 1;
  }

  const languages = Array.from(
    new Set(
      questions.flatMap(
        (question) => question.localizations?.map((localization) => localization.languageCode) ?? []
      )
    )
  ).filter((language): language is string => language !== undefined);

  const validationWarnings: string[] = [];
  if (questionIds.length === 0) {
    validationWarnings.push("Add at least one question before publishing.");
  }
  const defaultLanguage = quizQuery.data?.defaultLanguage;
  if (defaultLanguage && questions.length > 0) {
    const untranslated = questions.filter(
      (question) =>
        !question.localizations?.some(
          (localization) => localization.languageCode === defaultLanguage
        )
    );
    if (untranslated.length > 0) {
      validationWarnings.push(
        `${untranslated.length} question${untranslated.length === 1 ? " is" : "s are"} not translated into ${defaultLanguage}.`
      );
    }
  }

  const publishMutation = usePublishQuiz();

  return {
    quiz: quizQuery.data,
    questions,
    isLoading: quizQuery.isPending || isLoadingQuestions,
    questionCount: questionIds.length,
    difficultyDistribution,
    languages,
    validationWarnings,
    canPublish: validationWarnings.length === 0 && quizQuery.data?.state === "DRAFT",
    publish: () => publishMutation.mutateAsync(quizId),
    isPublishing: publishMutation.isPending,
    publishError: publishMutation.error
  };
}
