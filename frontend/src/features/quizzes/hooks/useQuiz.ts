import { useQuery } from "@tanstack/react-query";
import { quizApi } from "@/api/quizApi";
import { quizKeys } from "@/features/quizzes/queryKeys";

/** A single quiz — the full editable representation, for the editor. */
export function useQuiz(quizId: string | undefined) {
  return useQuery({
    queryKey: quizKeys.detail(quizId ?? ""),
    queryFn: () => quizApi.getById(quizId!),
    enabled: quizId !== undefined
  });
}
