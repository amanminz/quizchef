import { useQuery } from "@tanstack/react-query";
import { questionApi, type QuestionLibraryFilters } from "@/api/questionApi";
import { questionKeys } from "@/features/questions/queryKeys";

/** The caller's own question library — filtered, paged, never cross-author. */
export function useQuestionLibrary(filters: QuestionLibraryFilters = {}) {
  return useQuery({
    queryKey: questionKeys.library(filters),
    queryFn: () => questionApi.search(filters)
  });
}
