import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useCreateSession } from "@/features/sessions/hooks/useCreateSession";
import { useQuiz } from "@/features/quizzes/hooks/useQuiz";
import { useQuizzes } from "@/features/quizzes/hooks/useQuizzes";

/**
 * Orchestrates the create-session step of the hosting workflow (published
 * quiz → session): the published-quiz search, the selection, the selected
 * quiz's full detail (question count and settings, for the metadata
 * panel), and the server-confirmed create. On success the host lands on
 * the session's detail page to review the server-assigned PIN and settings
 * before opening the lobby.
 *
 * Only published quizzes are offered — the same rule the server enforces
 * (409 quiz.not-published), previewed here as a filter, never as a client
 * verdict.
 */
export function useSessionHosting() {
  const navigate = useNavigate();
  const [search, setSearch] = useState("");
  const [selectedQuizId, setSelectedQuizId] = useState<string | undefined>(undefined);

  const publishedQuizzesQuery = useQuizzes({
    state: "PUBLISHED",
    search: search.trim() || undefined,
    size: 50,
    sort: "updatedAt,desc"
  });
  const selectedQuizQuery = useQuiz(selectedQuizId);
  const createMutation = useCreateSession();

  const selectedQuiz = selectedQuizQuery.data;
  const questionCount = selectedQuiz?.questionIds?.length ?? 0;
  const secondsPerQuestion = selectedQuiz?.settings?.questionTimeLimitSeconds;
  const estimatedDurationMinutes =
    secondsPerQuestion && questionCount > 0
      ? Math.max(1, Math.round((questionCount * secondsPerQuestion) / 60))
      : undefined;

  const createSession = async () => {
    if (!selectedQuizId) {
      return;
    }
    // The published quiz version id is the quiz's own id today (see
    // useCreateSession); the server re-validates that it is published.
    // Failure surfaces through createError, not through this promise.
    const session = await createMutation.mutateAsync(selectedQuizId).catch(() => undefined);
    if (session?.sessionId) {
      navigate(`/sessions/${session.sessionId}`);
    }
  };

  return {
    search,
    setSearch,
    publishedQuizzes: publishedQuizzesQuery.data?.items ?? [],
    isLoadingQuizzes: publishedQuizzesQuery.isPending,
    quizzesError: publishedQuizzesQuery.error,
    refetchQuizzes: publishedQuizzesQuery.refetch,
    selectedQuizId,
    selectQuiz: setSelectedQuizId,
    selectedQuiz,
    isLoadingSelectedQuiz: selectedQuizId !== undefined && selectedQuizQuery.isPending,
    questionCount,
    secondsPerQuestion,
    estimatedDurationMinutes,
    createSession,
    isCreating: createMutation.isPending,
    createError: createMutation.error
  };
}
