import type { QuestionLibraryFilters } from "@/api/questionApi";

/**
 * Centralized TanStack Query keys for the question feature — one place so
 * a mutation's invalidation can never drift from a query's key by a typo.
 * Filters are part of the key: a different filter set is a different
 * cached page, exactly as it should be. Lived in the quizzes feature while
 * the picker was the only consumer; owned by the questions feature since
 * authoring arrived (Phase 3 PR #4A).
 */
export const questionKeys = {
  all: ["questions"] as const,
  libraries: () => [...questionKeys.all, "library"] as const,
  library: (filters: QuestionLibraryFilters) => [...questionKeys.libraries(), filters] as const,
  detail: (questionId: string) => [...questionKeys.all, "detail", questionId] as const,
  usage: (questionId: string) => [...questionKeys.all, "usage", questionId] as const
};
