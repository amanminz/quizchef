import { useMutation, useQueryClient } from "@tanstack/react-query";
import { quizApi } from "@/api/quizApi";
import { quizKeys } from "@/features/quizzes/queryKeys";

/**
 * Publishes a draft quiz. A lifecycle transition, not a reversible edit —
 * server-confirmed only, deliberately no optimistic UI (unlike
 * attach/detach/reorder, which are naturally reversible).
 */
export function usePublishQuiz() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (quizId: string) => quizApi.publish(quizId),
    onSuccess: (quiz, quizId) => {
      queryClient.setQueryData(quizKeys.detail(quizId), quiz);
      queryClient.invalidateQueries({ queryKey: quizKeys.lists() });
    }
  });
}
