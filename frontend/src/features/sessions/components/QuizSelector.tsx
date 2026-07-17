import { Search } from "lucide-react";
import { Spinner } from "@/components/common/Spinner";
import { cn } from "@/utils/cn";
import type { QuizSummaryResponse } from "@/types/api";

export interface QuizSelectorProps {
  quizzes: QuizSummaryResponse[];
  isLoading: boolean;
  search: string;
  onSearchChange: (search: string) => void;
  selectedQuizId: string | undefined;
  onSelect: (quizId: string) => void;
}

/**
 * The searchable published-quiz picker for session creation. Only
 * published quizzes are ever passed in (the page queries with
 * state=PUBLISHED) — the same rule the server enforces on create with a
 * 409. Radio semantics so keyboard and screen-reader users get a real
 * single-choice control.
 */
export function QuizSelector({
  quizzes,
  isLoading,
  search,
  onSearchChange,
  selectedQuizId,
  onSelect
}: QuizSelectorProps) {
  return (
    <div className="flex flex-col gap-3">
      <label className="relative block">
        <span className="sr-only">Search published quizzes</span>
        <Search
          aria-hidden
          className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
        />
        <input
          type="search"
          value={search}
          onChange={(event) => onSearchChange(event.target.value)}
          placeholder="Search published quizzes…"
          className="h-10 w-full rounded-md border border-input bg-transparent pl-9 pr-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        />
      </label>

      {isLoading && (
        <div className="flex justify-center py-8">
          <Spinner className="text-primary" />
        </div>
      )}

      {!isLoading && quizzes.length === 0 && (
        <p className="rounded-md border border-dashed px-4 py-8 text-center text-sm text-muted-foreground">
          No published quizzes{search ? " match this search" : " yet — publish a quiz first"}.
        </p>
      )}

      {!isLoading && quizzes.length > 0 && (
        <div role="radiogroup" aria-label="Published quizzes" className="flex flex-col gap-2">
          {quizzes.map((quiz) => {
            const selected = quiz.id === selectedQuizId;
            return (
              <button
                key={quiz.id}
                type="button"
                role="radio"
                aria-checked={selected}
                onClick={() => quiz.id && onSelect(quiz.id)}
                className={cn(
                  "flex items-center justify-between gap-3 rounded-md border px-4 py-3 text-left text-sm transition-colors",
                  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                  selected ? "border-primary bg-primary/5" : "hover:bg-muted"
                )}
              >
                <span className="min-w-0">
                  <span className="block truncate font-medium">{quiz.title}</span>
                  {quiz.description && (
                    <span className="block truncate text-muted-foreground">{quiz.description}</span>
                  )}
                </span>
                <span className="shrink-0 text-xs text-muted-foreground">
                  {quiz.questionCount ?? 0} question{quiz.questionCount === 1 ? "" : "s"}
                </span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
