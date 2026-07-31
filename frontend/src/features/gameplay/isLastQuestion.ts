import type { CurrentQuestionResponse } from "@/types/api";

/**
 * Whether the given question is the quiz's last — the boundary past which
 * ranking neighbours are never shown (final standings are held for the
 * host's winner ceremony instead). Shared by host and participant
 * orchestration so both agree on the same rule.
 */
export function isLastQuestion(question: CurrentQuestionResponse | undefined): boolean {
  return (
    question?.questionNumber != null &&
    question?.totalQuestions != null &&
    question.questionNumber >= question.totalQuestions
  );
}
