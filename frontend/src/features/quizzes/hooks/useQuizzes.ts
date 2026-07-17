import { useQuery } from "@tanstack/react-query";
import { quizApi, type QuizListFilters } from "@/api/quizApi";
import { quizKeys } from "@/features/quizzes/queryKeys";

/** "My Quizzes" — owner-scoped, filtered, paged. */
export function useQuizzes(filters: QuizListFilters = {}) {
  return useQuery({
    queryKey: quizKeys.list(filters),
    queryFn: () => quizApi.listMine(filters)
  });
}
