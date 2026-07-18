import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import type { QuestionLibraryFilters } from "@/api/questionApi";
import { Button } from "@/components/common/Button";
import { EntityStatusBadge } from "@/components/common/EntityStatusBadge";
import { Spinner } from "@/components/common/Spinner";
import { EmptyLibraryState } from "@/features/quizzes/components/EmptyLibraryState";
import { QuestionRow } from "@/features/quizzes/components/QuestionRow";
import { lifecycleStateTone } from "@/features/quizzes/statusTone";
import type { Difficulty, QuestionSummaryResponse } from "@/types/api";

export interface QuestionPickerProps {
  questions: QuestionSummaryResponse[];
  /** Already attached — filtered out of the addable list. */
  selectedQuestionIds: string[];
  onAdd: (questionId: string) => void;
  addingQuestionId?: string;
  isLoading?: boolean;
  filters: QuestionLibraryFilters;
  onFiltersChange: (filters: QuestionLibraryFilters) => void;
  /** Where a draft row's "Publish draft" link goes — supplied by the page so the picker stays route-agnostic. */
  editDraftHref?: (questionId: string) => string;
  /** CTA rendered when the library is truly empty (no filters active). */
  emptyAction?: ReactNode;
}

const DIFFICULTIES: Difficulty[] = ["EASY", "MEDIUM", "HARD"];

/**
 * Search + filter bar over the caller's question library, rendering the
 * addable results. Purely presentational: the page owns fetching
 * (useQuestionLibrary) and filter state; this component only renders what
 * it's given and reports changes upward. Drafts are attachable — an author
 * composes a quiz while its questions are still being refined — and carry
 * their status plus an edit path; only archived questions are retired from
 * new attachment.
 */
export function QuestionPicker({
  questions,
  selectedQuestionIds,
  onAdd,
  addingQuestionId,
  isLoading,
  filters,
  onFiltersChange,
  editDraftHref,
  emptyAction
}: QuestionPickerProps) {
  const addable = questions.filter((question) => !selectedQuestionIds.includes(question.id ?? ""));
  const isFiltered = Object.values(filters).some((value) => value !== undefined);

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap gap-2">
        <input
          type="search"
          placeholder="Search questions"
          value={filters.search ?? ""}
          onChange={(event) =>
            onFiltersChange({ ...filters, search: event.target.value || undefined })
          }
          className="h-9 flex-1 min-w-[10rem] rounded-md border border-input bg-background px-3 text-sm"
          aria-label="Search questions"
        />
        <select
          value={filters.difficulty ?? ""}
          onChange={(event) =>
            onFiltersChange({
              ...filters,
              difficulty: (event.target.value || undefined) as Difficulty | undefined
            })
          }
          className="h-9 rounded-md border border-input bg-background px-2 text-sm"
          aria-label="Filter by difficulty"
        >
          <option value="">Any difficulty</option>
          {DIFFICULTIES.map((difficulty) => (
            <option key={difficulty} value={difficulty}>
              {difficulty}
            </option>
          ))}
        </select>
        <input
          type="text"
          placeholder="Language (e.g. en)"
          value={filters.language ?? ""}
          onChange={(event) =>
            onFiltersChange({ ...filters, language: event.target.value || undefined })
          }
          className="h-9 w-32 rounded-md border border-input bg-background px-3 text-sm"
          aria-label="Filter by language"
        />
      </div>

      {isLoading ? (
        <div className="flex justify-center py-10">
          <Spinner className="text-primary" />
        </div>
      ) : addable.length === 0 ? (
        <EmptyLibraryState
          isFiltered={isFiltered}
          action={
            isFiltered ? (
              <Button variant="secondary" onClick={() => onFiltersChange({})}>
                Clear filters
              </Button>
            ) : (
              emptyAction
            )
          }
        />
      ) : (
        <div className="flex flex-col gap-2">
          {addable.map((question) => {
            const isDraft = question.state === "DRAFT";
            const isArchived = question.state === "ARCHIVED";
            return (
              <QuestionRow
                key={question.id}
                question={question}
                action="add"
                onAction={onAdd}
                isPending={addingQuestionId === question.id}
                disabledReason={isArchived ? "Archived — no longer attachable" : undefined}
                meta={
                  question.state === "PUBLISHED" ? undefined : (
                    <>
                      <EntityStatusBadge
                        label={question.state ?? "DRAFT"}
                        tone={lifecycleStateTone(question.state)}
                      />
                      {isDraft && editDraftHref && question.id && (
                        <Link
                          to={editDraftHref(question.id)}
                          className="text-xs font-medium text-primary hover:underline"
                        >
                          Edit draft
                        </Link>
                      )}
                    </>
                  )
                }
              />
            );
          })}
        </div>
      )}
    </div>
  );
}
