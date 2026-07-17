import type { EntityStatusTone } from "@/components/common/EntityStatusBadge";
import type { QuestionState, QuizState } from "@/types/api";

/** Maps the quiz/question lifecycle (shared vocabulary, RFC-003) to a badge tone. */
export function lifecycleStateTone(state: QuizState | QuestionState | undefined): EntityStatusTone {
  switch (state) {
    case "DRAFT":
      return "warning";
    case "PUBLISHED":
      return "positive";
    case "ARCHIVED":
      return "neutral";
    default:
      return "neutral";
  }
}
