/**
 * A participant's outcome for one question, as a display concern. This is
 * NOT a client-side correctness calculation: both inputs are server facts
 * (the participant's accepted submission, and the correct set the server
 * revealed), and set-equality is the very rule SubmitAnswerApplicationService
 * scores by — the client merely renders the comparison the server already
 * made. The wire carries no per-answer verdict field (noted in RFC-009 as
 * a contract adaptation); if one ever lands, this helper is deleted.
 */
export type AnswerVerdict = "correct" | "incorrect" | "unanswered";

export function verdictFor(
  submittedOptionIds: string[] | undefined,
  correctOptionIds: string[] | undefined
): AnswerVerdict {
  if (!submittedOptionIds || submittedOptionIds.length === 0) {
    return "unanswered";
  }
  if (!correctOptionIds) {
    return "unanswered";
  }
  const submitted = new Set(submittedOptionIds);
  const correct = new Set(correctOptionIds);
  const equal =
    submitted.size === correct.size && [...submitted].every((id) => correct.has(id));
  return equal ? "correct" : "incorrect";
}
