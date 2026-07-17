import { useMutation, useQueryClient } from "@tanstack/react-query";
import { quizApi } from "@/api/quizApi";
import { useQuiz } from "@/features/quizzes/hooks/useQuiz";
import { quizKeys } from "@/features/quizzes/queryKeys";
import type { QuizResponse } from "@/types/api";

interface MutationContext {
  previous: QuizResponse | undefined;
}

/**
 * Orchestrates composing a quiz's questions: attach, detach, reorder.
 * These three are naturally reversible (unlike publish/archive), so all
 * three are optimistic — the composition updates instantly, and rolls
 * back to the server's last known state if the request fails. `onSettled`
 * always refetches so the client reconciles to the server's truth
 * (ADR-006 spirit: optimistic UI is a rendering convenience, never a
 * source of truth) even when a mutation succeeds.
 */
export function useQuestionSelection(quizId: string) {
  const queryClient = useQueryClient();
  const quizQuery = useQuiz(quizId);

  function optimisticallyUpdate(updater: (previous: QuizResponse) => QuizResponse) {
    return async (): Promise<MutationContext> => {
      await queryClient.cancelQueries({ queryKey: quizKeys.detail(quizId) });
      const previous = queryClient.getQueryData<QuizResponse>(quizKeys.detail(quizId));
      if (previous) {
        queryClient.setQueryData(quizKeys.detail(quizId), updater(previous));
      }
      return { previous };
    };
  }

  function rollback(context: MutationContext | undefined) {
    if (context?.previous) {
      queryClient.setQueryData(quizKeys.detail(quizId), context.previous);
    }
  }

  function reconcile() {
    queryClient.invalidateQueries({ queryKey: quizKeys.detail(quizId) });
  }

  const attachMutation = useMutation({
    mutationFn: (questionId: string) => quizApi.attachQuestion(quizId, questionId),
    onMutate: (questionId) =>
      optimisticallyUpdate((previous) => ({
        ...previous,
        questionIds: [...(previous.questionIds ?? []), questionId]
      }))(),
    onError: (_error, _questionId, context) => rollback(context),
    onSettled: reconcile
  });

  const detachMutation = useMutation({
    mutationFn: (questionId: string) => quizApi.detachQuestion(quizId, questionId),
    onMutate: (questionId) =>
      optimisticallyUpdate((previous) => ({
        ...previous,
        questionIds: (previous.questionIds ?? []).filter((id) => id !== questionId)
      }))(),
    onError: (_error, _questionId, context) => rollback(context),
    onSettled: reconcile
  });

  const reorderMutation = useMutation({
    mutationFn: (questionIds: string[]) => quizApi.reorderQuestions(quizId, questionIds),
    onMutate: (questionIds) =>
      optimisticallyUpdate((previous) => ({ ...previous, questionIds }))(),
    onError: (_error, _questionIds, context) => rollback(context),
    onSettled: reconcile
  });

  return {
    quiz: quizQuery.data,
    selectedQuestionIds: quizQuery.data?.questionIds ?? [],
    isLoading: quizQuery.isPending,
    attach: attachMutation.mutate,
    isAttaching: attachMutation.isPending,
    /** The question id currently being attached, for a per-row pending state. */
    attachingQuestionId: attachMutation.isPending ? attachMutation.variables : undefined,
    attachError: attachMutation.error,
    detach: detachMutation.mutate,
    isDetaching: detachMutation.isPending,
    detachError: detachMutation.error,
    reorder: reorderMutation.mutate,
    isReordering: reorderMutation.isPending,
    reorderError: reorderMutation.error
  };
}
