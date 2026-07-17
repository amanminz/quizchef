import { useMutation } from "@tanstack/react-query";
import { useCallback, useEffect, useState } from "react";
import { isApiClientError } from "@/api/apiError";
import { sessionApi } from "@/api/sessionApi";
import type { CurrentQuestionResponse, SessionSnapshotResponse } from "@/types/api";

/**
 * One participant's answer, per question. Duplicate submission is
 * prevented two ways: locally (`hasSubmitted` guards the call before it
 * ever leaves the browser) and by the server, which is the real authority
 * — a `session.answer.not-accepted` 409 (already answered, or the
 * question is no longer open) is treated as "you're done with this
 * question" rather than a failure, since that is exactly what it means.
 * Any other error is a genuine submission failure and surfaces as one.
 *
 * Submitted state is keyed by `questionId`, so a new question always
 * starts unsubmitted with no explicit reset, and a snapshot's
 * `submittedOptionIds` (reconnect/refresh recovery) seeds the same map —
 * refreshing mid-question restores exactly what was already sent.
 */
export function useAnswerSubmission(
  sessionId: string | undefined,
  participantId: string | undefined,
  question: CurrentQuestionResponse | undefined,
  snapshot: SessionSnapshotResponse | undefined
) {
  const [submittedByQuestion, setSubmittedByQuestion] = useState<Record<string, string[]>>({});

  useEffect(() => {
    if (snapshot?.currentQuestionId && snapshot.submittedOptionIds && snapshot.submittedOptionIds.length > 0) {
      const questionId = snapshot.currentQuestionId;
      const optionIds = snapshot.submittedOptionIds;
      setSubmittedByQuestion((previous) => ({ ...previous, [questionId]: optionIds }));
    }
  }, [snapshot]);

  const questionId = question?.questionId;
  const submittedOptionIds = questionId ? submittedByQuestion[questionId] : undefined;
  const hasSubmitted = submittedOptionIds !== undefined;

  const mutation = useMutation({
    mutationFn: (selectedOptionIds: string[]) =>
      sessionApi.submitAnswer(sessionId!, { participantId: participantId!, questionId: questionId!, selectedOptionIds }),
    onSuccess: (_response, selectedOptionIds) => {
      if (questionId) {
        setSubmittedByQuestion((previous) => ({ ...previous, [questionId]: selectedOptionIds }));
      }
    },
    onError: (error, selectedOptionIds) => {
      if (questionId && isApiClientError(error) && error.code === "session.answer.not-accepted") {
        setSubmittedByQuestion((previous) => ({ ...previous, [questionId]: selectedOptionIds }));
      }
    }
  });

  const submit = useCallback(
    (selectedOptionIds: string[]) => {
      if (!sessionId || !participantId || !questionId || hasSubmitted) {
        return;
      }
      if (question?.phase !== "QUESTION_OPEN") {
        return;
      }
      mutation.mutate(selectedOptionIds);
    },
    [sessionId, participantId, questionId, hasSubmitted, question?.phase, mutation]
  );

  const isRealSubmitError =
    mutation.isError &&
    !(isApiClientError(mutation.error) && mutation.error.code === "session.answer.not-accepted");

  return {
    hasSubmitted,
    submittedOptionIds,
    submit,
    isSubmitting: mutation.isPending,
    submitError: isRealSubmitError ? mutation.error : null
  };
}
