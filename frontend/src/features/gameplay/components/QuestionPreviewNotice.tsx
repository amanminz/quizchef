import { BookOpen } from "lucide-react";

/**
 * Rendered in place of the answer surface while a question is in its
 * reading period (`QUESTION_PREVIEW`) — the participant's device never
 * received option data to begin with, so there is nothing to disable,
 * only this notice to show instead.
 */
export function QuestionPreviewNotice() {
  return (
    <div
      role="status"
      className="flex flex-col items-center gap-2 rounded-lg border border-dashed px-6 py-10 text-center"
    >
      <BookOpen aria-hidden className="h-6 w-6 text-muted-foreground" />
      <p className="text-sm font-medium text-muted-foreground">
        Get ready — options will appear shortly
      </p>
    </div>
  );
}
