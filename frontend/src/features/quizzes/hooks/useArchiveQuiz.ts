import { useMutation, useQueryClient } from "@tanstack/react-query";
import { quizApi } from "@/api/quizApi";
import { quizKeys } from "@/features/quizzes/queryKeys";

/**
 * Archives a published quiz. A lifecycle transition — server-confirmed
 * only, no optimistic UI.
 */
export function useArchiveQuiz() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (quizId: string) => quizApi.archive(quizId),
    onSuccess: (quiz, quizId) => {
      queryClient.setQueryData(quizKeys.detail(quizId), quiz);
      queryClient.invalidateQueries({ queryKey: quizKeys.lists() });
    }
  });
}
