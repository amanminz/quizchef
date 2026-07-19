import { Users } from "lucide-react";
import type { AnswerProgressResponse } from "@/types/api";
import { cn } from "@/utils/cn";

export interface AnswerProgressBadgeProps {
  progress: AnswerProgressResponse | undefined;
  /** Everyone eligible has answered — the moment worth emphasizing. */
  emphasized?: boolean;
}

/**
 * The host's "5 / 10 answered" — the backend's authoritative counts,
 * counts only, never who. Hidden entirely while the counts are unknown
 * (loading, between questions) instead of showing empty values.
 */
export function AnswerProgressBadge({ progress, emphasized = false }: AnswerProgressBadgeProps) {
  if (!progress) {
    return null;
  }
  return (
    <p
      role="status"
      className={cn(
        "inline-flex items-center gap-2 self-start rounded-full border px-4 py-1.5 text-lg font-semibold tabular-nums",
        emphasized ? "border-success bg-success/10 text-success" : "border-border text-foreground"
      )}
    >
      <Users aria-hidden className="h-5 w-5" />
      {progress.answeredCount} / {progress.eligibleCount} answered
      {emphasized && <span className="text-sm font-medium">— everyone's in</span>}
    </p>
  );
}
