import { useQuery } from "@tanstack/react-query";
import { questionApi } from "@/api/questionApi";
import { questionKeys } from "@/features/questions/queryKeys";

/** One question's full representation — shared cache with the review page's per-question loads. */
export function useQuestion(questionId: string | undefined) {
  return useQuery({
    queryKey: questionKeys.detail(questionId ?? ""),
    queryFn: () => questionApi.getById(questionId ?? ""),
    enabled: Boolean(questionId)
  });
}
