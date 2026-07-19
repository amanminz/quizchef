import { useMutation, useQueryClient } from "@tanstack/react-query";
import { questionApi } from "@/api/questionApi";
import { questionKeys } from "@/features/questions/queryKeys";
import type { CreateQuestionRequest, QuestionResponse, UpdateQuestionRequest } from "@/types/api";

/**
 * The question lifecycle's write side: create draft, update draft,
 * publish, archive. None are optimistic — authoring changes are rare and
 * consequential (publish freezes content), so every mutation settles
 * against the server and then invalidates the library plus the question's
 * detail entry. Composition (attach/detach/reorder) stays in the quizzes
 * feature (useQuestionSelection): it mutates the quiz aggregate, not the
 * question.
 */
export function useQuestionAuthoring() {
  const queryClient = useQueryClient();

  function reconcile(question: QuestionResponse) {
    if (question.id) {
      queryClient.setQueryData(questionKeys.detail(question.id), question);
    }
    queryClient.invalidateQueries({ queryKey: questionKeys.libraries() });
  }

  const createMutation = useMutation({
    mutationFn: (request: CreateQuestionRequest) => questionApi.create(request),
    onSuccess: reconcile
  });

  const updateMutation = useMutation({
    mutationFn: ({ questionId, request }: { questionId: string; request: UpdateQuestionRequest }) =>
      questionApi.update(questionId, request),
    onSuccess: reconcile
  });

  const publishMutation = useMutation({
    mutationFn: (questionId: string) => questionApi.publish(questionId),
    onSuccess: reconcile
  });

  const archiveMutation = useMutation({
    mutationFn: (questionId: string) => questionApi.archive(questionId),
    onSuccess: reconcile
  });

  const restoreMutation = useMutation({
    mutationFn: (questionId: string) => questionApi.restore(questionId),
    onSuccess: reconcile
  });

  const deleteMutation = useMutation({
    mutationFn: (questionId: string) => questionApi.delete(questionId),
    onSuccess: (_, questionId) => {
      queryClient.removeQueries({ queryKey: questionKeys.detail(questionId) });
      queryClient.invalidateQueries({ queryKey: questionKeys.libraries() });
    }
  });

  return {
    create: createMutation.mutateAsync,
    isCreating: createMutation.isPending,
    update: updateMutation.mutateAsync,
    isUpdating: updateMutation.isPending,
    publish: publishMutation.mutateAsync,
    isPublishing: publishMutation.isPending,
    archive: archiveMutation.mutateAsync,
    isArchiving: archiveMutation.isPending,
    restore: restoreMutation.mutateAsync,
    isRestoring: restoreMutation.isPending,
    deleteQuestion: deleteMutation.mutateAsync,
    isDeleting: deleteMutation.isPending
  };
}
