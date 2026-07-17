import type { QuestionLibraryFilters } from "@/api/questionApi";
import { Spinner } from "@/components/common/Spinner";
import { EmptyLibraryState } from "@/features/quizzes/components/EmptyLibraryState";
import { QuestionRow } from "@/features/quizzes/components/QuestionRow";
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
}

const DIFFICULTIES: Difficulty[] = ["EASY", "MEDIUM", "HARD"];

/**
 * Search + filter bar over the caller's question library, rendering the
 * addable results. Purely presentational: the page owns fetching
 * (useQuestionLibrary) and filter state; this component only renders what
 * it's given and reports changes upward.
 */
export function QuestionPicker({
  questions,
  selectedQuestionIds,
  onAdd,
  addingQuestionId,
  isLoading,
  filters,
  onFiltersChange
}: QuestionPickerProps) {
  const addable = questions.filter((question) => !selectedQuestionIds.includes(question.id ?? ""));

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap gap-2">
        <input
          type="search"
          placeholder="Search questions"
          value={filters.search ?? ""}
          onChange={(event) => onFiltersChange({ ...filters, search: event.target.value || undefined })}
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
          onChange={(event) => onFiltersChange({ ...filters, language: event.target.value || undefined })}
          className="h-9 w-32 rounded-md border border-input bg-background px-3 text-sm"
          aria-label="Filter by language"
        />
      </div>

      {isLoading ? (
        <div className="flex justify-center py-10">
          <Spinner className="text-primary" />
        </div>
      ) : addable.length === 0 ? (
        <EmptyLibraryState isFiltered={Object.values(filters).some((value) => value !== undefined)} />
      ) : (
        <div className="flex flex-col gap-2">
          {addable.map((question) => (
            <QuestionRow
              key={question.id}
              question={question}
              action="add"
              onAction={onAdd}
              isPending={addingQuestionId === question.id}
            />
          ))}
        </div>
      )}
    </div>
  );
}
