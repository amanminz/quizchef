import { useMutation, useQueryClient } from "@tanstack/react-query";
import { quizApi } from "@/api/quizApi";
import { useQuiz } from "@/features/quizzes/hooks/useQuiz";
import { quizKeys } from "@/features/quizzes/queryKeys";
import type { CreateQuizRequest, UpdateQuizRequest } from "@/types/api";

/**
 * Orchestrates creating and editing a quiz's metadata — the only place
 * this workflow's logic lives (pages render, this hook coordinates).
 * Create and update are server-confirmed; the version required by
 * `UpdateQuizRequest` protects against a lost update (RFC-003), not
 * something this hook papers over — a 409 surfaces to the caller as a
 * normal mutation error.
 */
export function useQuizAuthoring(quizId: string | undefined) {
  const queryClient = useQueryClient();
  const quizQuery = useQuiz(quizId);

  const createMutation = useMutation({
    mutationFn: (request: CreateQuizRequest) => quizApi.create(request),
    onSuccess: (quiz) => {
      if (quiz.id) {
        queryClient.setQueryData(quizKeys.detail(quiz.id), quiz);
      }
      queryClient.invalidateQueries({ queryKey: quizKeys.lists() });
    }
  });

  const updateMutation = useMutation({
    mutationFn: (request: UpdateQuizRequest) => quizApi.update(quizId!, request),
    onSuccess: (quiz) => {
      if (quizId) {
        queryClient.setQueryData(quizKeys.detail(quizId), quiz);
      }
      queryClient.invalidateQueries({ queryKey: quizKeys.lists() });
    }
  });

  return {
    quiz: quizQuery.data,
    isLoading: quizQuery.isPending,
    error: quizQuery.error,
    create: createMutation.mutateAsync,
    isCreating: createMutation.isPending,
    createError: createMutation.error,
    update: updateMutation.mutateAsync,
    isUpdating: updateMutation.isPending,
    updateError: updateMutation.error
  };
}
