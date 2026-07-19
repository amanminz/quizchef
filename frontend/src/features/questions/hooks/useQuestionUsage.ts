import { useQuery } from "@tanstack/react-query";
import { questionApi } from "@/api/questionApi";
import { questionKeys } from "@/features/questions/queryKeys";

/**
 * How many quizzes compose a question — what the detail page frames the
 * delete affordance with. The backend enforces the actual rule
 * transactionally; this read only shapes the UI.
 */
export function useQuestionUsage(questionId: string | undefined) {
  return useQuery({
    queryKey: questionKeys.usage(questionId ?? ""),
    queryFn: () => questionApi.usage(questionId ?? ""),
    enabled: Boolean(questionId)
  });
}
