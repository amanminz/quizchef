import { useMutation, useQueryClient } from "@tanstack/react-query";
import { sessionApi } from "@/api/sessionApi";
import { gameplayKeys } from "@/features/gameplay/queryKeys";
import { sessionKeys } from "@/features/sessions/queryKeys";
import type { CorrectQuestionRequest } from "@/types/api";

/**
 * The host's two recoveries for a bad question: correct it, or remove it.
 *
 * Both are server-confirmed and never optimistic, for a stronger reason
 * than the usual one. What each does depends on state only the server
 * knows — whether the question is the one in play, whether anyone answered
 * it, whether anything follows it — so there is no outcome this client
 * could predict and paint ahead of the response. It issues the command and
 * re-reads.
 *
 * Invalidation is wide on purpose. A correction or removal moves the phase,
 * the numbering, every score, the standings, the distribution, and the
 * answer progress at once; anything narrower would leave one of them
 * showing a question that no longer exists. Scores are never patched here —
 * the backend reverses them (ADR-006), and this only asks to see the
 * result.
 */
export function useLiveQuestionRecovery(sessionId: string | undefined) {
  const queryClient = useQueryClient();

  const invalidateEverythingTheQuestionTouched = () => {
    if (!sessionId) {
      return;
    }
    void queryClient.invalidateQueries({ queryKey: sessionKeys.detail(sessionId) });
    void queryClient.invalidateQueries({ queryKey: sessionKeys.questions(sessionId) });
    void queryClient.invalidateQueries({ queryKey: gameplayKeys.currentQuestion(sessionId) });
    void queryClient.invalidateQueries({ queryKey: gameplayKeys.answerProgress(sessionId) });
    void queryClient.invalidateQueries({ queryKey: gameplayKeys.answerDistribution(sessionId) });
    void queryClient.invalidateQueries({ queryKey: gameplayKeys.results(sessionId) });
    void queryClient.invalidateQueries({ queryKey: gameplayKeys.topFiveTransitions(sessionId) });
    void queryClient.invalidateQueries({ queryKey: gameplayKeys.finalStandings(sessionId) });
  };

  const correct = useMutation({
    mutationFn: ({
      questionId,
      request
    }: {
      questionId: string;
      request: CorrectQuestionRequest;
    }) => sessionApi.correctQuestion(sessionId!, questionId, request),
    onSuccess: invalidateEverythingTheQuestionTouched
  });

  const remove = useMutation({
    mutationFn: (questionId: string) => sessionApi.removeQuestion(sessionId!, questionId),
    onSuccess: invalidateEverythingTheQuestionTouched
  });

  return {
    correctQuestion: (questionId: string, request: CorrectQuestionRequest) =>
      correct.mutateAsync({ questionId, request }),
    isCorrecting: correct.isPending,
    correctError: correct.error,
    removeQuestion: (questionId: string) => remove.mutateAsync(questionId),
    isRemoving: remove.isPending,
    removeError: remove.error
  };
}
