import type { ReactNode } from "react";
import { Button } from "@/components/common/Button";
import { DifficultyBadge } from "@/features/quizzes/components/DifficultyBadge";
import { LanguageChip } from "@/features/quizzes/components/LanguageChip";
import type { QuestionSummaryResponse } from "@/types/api";

export interface QuestionRowProps {
  question: QuestionSummaryResponse;
  /** "add" for a library row not yet attached; "remove" for one already on the quiz. */
  action: "add" | "remove";
  onAction: (questionId: string) => void;
  isPending?: boolean;
  /** Optional leading slot — a drag handle, supplied by whatever composes reordering. */
  leading?: ReactNode;
  /** Extra badges or links rendered with the metadata (status pill, edit link). */
  meta?: ReactNode;
  /** Disables the action and states why — e.g. a draft is not attachable yet. */
  disabledReason?: string;
}

/**
 * One question preview: title, difficulty, tags, language availability,
 * and a single action. Presentational and DnD-agnostic on purpose — the
 * questions page wraps it in whatever reorder mechanism it uses.
 */
export function QuestionRow({
  question,
  action,
  onAction,
  isPending,
  leading,
  meta,
  disabledReason
}: QuestionRowProps) {
  const questionId = question.id ?? "";

  return (
    <div className="flex items-center gap-3 rounded-md border border-border px-3 py-2">
      {leading}
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{question.title}</p>
        <div className="mt-1 flex flex-wrap items-center gap-1.5">
          {meta}
          {question.difficulty && <DifficultyBadge difficulty={question.difficulty} />}
          {question.availableLanguages?.map((language) => (
            <LanguageChip key={language} language={language} />
          ))}
          {question.tags?.map((tag) => (
            <span key={tag.id} className="text-xs text-muted-foreground">
              #{tag.name}
            </span>
          ))}
        </div>
        {disabledReason && <p className="mt-1 text-xs text-muted-foreground">{disabledReason}</p>}
      </div>
      <Button
        variant={action === "remove" ? "destructive" : "secondary"}
        size="sm"
        onClick={() => onAction(questionId)}
        isLoading={isPending}
        disabled={disabledReason !== undefined}
      >
        {action === "add" ? "Add" : "Remove"}
      </Button>
    </div>
  );
}
