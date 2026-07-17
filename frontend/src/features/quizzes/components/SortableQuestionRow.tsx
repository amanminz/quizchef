import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVertical } from "lucide-react";
import { QuestionRow } from "@/features/quizzes/components/QuestionRow";
import type { QuestionSummaryResponse } from "@/types/api";

export interface SortableQuestionRowProps {
  question: QuestionSummaryResponse;
  onRemove: (questionId: string) => void;
  isPending?: boolean;
}

/** A QuestionRow made draggable via dnd-kit — the reorder wiring lives here, not in QuestionRow itself. */
export function SortableQuestionRow({ question, onRemove, isPending }: SortableQuestionRowProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: question.id ?? ""
  });

  return (
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={isDragging ? "opacity-60" : undefined}
    >
      <QuestionRow
        question={question}
        action="remove"
        onAction={onRemove}
        isPending={isPending}
        leading={
          <button
            type="button"
            aria-label={`Reorder ${question.title ?? "question"}`}
            className="cursor-grab touch-none text-muted-foreground hover:text-foreground active:cursor-grabbing"
            {...attributes}
            {...listeners}
          >
            <GripVertical aria-hidden className="h-4 w-4" />
          </button>
        }
      />
    </div>
  );
}
