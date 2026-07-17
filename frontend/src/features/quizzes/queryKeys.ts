import type { QuestionLibraryFilters } from "@/api/questionApi";
import type { QuizListFilters } from "@/api/quizApi";

/**
 * Centralized TanStack Query keys for the quiz-authoring feature — one
 * place so a mutation's invalidation can never drift from a query's key by
 * a typo. Filters are part of the key: a different filter set is a
 * different cached page, exactly as it should be.
 */
export const quizKeys = {
  all: ["quizzes"] as const,
  lists: () => [...quizKeys.all, "list"] as const,
  list: (filters: QuizListFilters) => [...quizKeys.lists(), filters] as const,
  detail: (quizId: string) => [...quizKeys.all, "detail", quizId] as const
};

export const questionKeys = {
  all: ["questions"] as const,
  libraries: () => [...questionKeys.all, "library"] as const,
  library: (filters: QuestionLibraryFilters) => [...questionKeys.libraries(), filters] as const,
  detail: (questionId: string) => [...questionKeys.all, "detail", questionId] as const
};
