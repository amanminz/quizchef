import {
  closestCenter,
  DndContext,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent
} from "@dnd-kit/core";
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy
} from "@dnd-kit/sortable";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import type { QuestionLibraryFilters } from "@/api/questionApi";
import { Button } from "@/components/common/Button";
import { EntityStatusBadge } from "@/components/common/EntityStatusBadge";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { ProgressStepper } from "@/components/common/ProgressStepper";
import { Spinner } from "@/components/common/Spinner";
import { WorkflowHeader } from "@/components/common/WorkflowHeader";
import { EmptyLibraryState } from "@/features/quizzes/components/EmptyLibraryState";
import { QuestionPicker } from "@/features/quizzes/components/QuestionPicker";
import { SortableQuestionRow } from "@/features/quizzes/components/SortableQuestionRow";
import { useQuestionLibrary } from "@/features/quizzes/hooks/useQuestionLibrary";
import { useQuestionSelection } from "@/features/quizzes/hooks/useQuestionSelection";
import { lifecycleStateTone } from "@/features/quizzes/statusTone";
import { AUTHORING_STEPS } from "@/features/quizzes/workflowSteps";

export function QuizQuestionsPage() {
  const { quizId = "" } = useParams<{ quizId: string }>();
  const [filters, setFilters] = useState<QuestionLibraryFilters>({});

  const selection = useQuestionSelection(quizId);
  const libraryQuery = useQuestionLibrary(filters);
  // Unfiltered pull so already-selected questions resolve to a summary
  // (title, difficulty, tags) regardless of the picker's active filters.
  const allMineQuery = useQuestionLibrary({ size: 200 });

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  if (selection.isLoading) {
    return (
      <PageContainer>
        <div className="flex justify-center py-16">
          <Spinner size="lg" className="text-primary" />
        </div>
      </PageContainer>
    );
  }

  if (!selection.quiz) {
    return (
      <PageContainer>
        <ErrorPanel error={new Error("Quiz not found")} />
      </PageContainer>
    );
  }

  const selectedSummaries = selection.selectedQuestionIds
    .map((id) => allMineQuery.data?.items?.find((question) => question.id === id))
    .filter((question): question is NonNullable<typeof question> => question !== undefined);

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (!over || active.id === over.id) {
      return;
    }
    const oldIndex = selection.selectedQuestionIds.indexOf(String(active.id));
    const newIndex = selection.selectedQuestionIds.indexOf(String(over.id));
    if (oldIndex === -1 || newIndex === -1) {
      return;
    }
    selection.reorder(arrayMove(selection.selectedQuestionIds, oldIndex, newIndex));
  }

  return (
    <PageContainer>
      <WorkflowHeader
        title="Questions"
        backHref={`/quizzes/${quizId}`}
        backLabel="Metadata"
        status={
          <EntityStatusBadge
            label={selection.quiz.state ?? "DRAFT"}
            tone={lifecycleStateTone(selection.quiz.state)}
          />
        }
      />
      <ProgressStepper steps={AUTHORING_STEPS} currentKey="questions" className="mb-6" />

      <div className="grid gap-8 lg:grid-cols-2">
        <section>
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
            Selected ({selection.selectedQuestionIds.length})
          </h2>
          {selection.selectedQuestionIds.length === 0 ? (
            <EmptyLibraryState isFiltered={false} />
          ) : (
            <DndContext
              sensors={sensors}
              collisionDetection={closestCenter}
              onDragEnd={handleDragEnd}
            >
              <SortableContext
                items={selection.selectedQuestionIds}
                strategy={verticalListSortingStrategy}
              >
                <div className="flex flex-col gap-2">
                  {selectedSummaries.map((question) => (
                    <SortableQuestionRow
                      key={question.id}
                      question={question}
                      onRemove={selection.detach}
                      isPending={selection.isDetaching || selection.isReordering}
                    />
                  ))}
                </div>
              </SortableContext>
            </DndContext>
          )}
        </section>

        <section>
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
            Library
          </h2>
          <QuestionPicker
            questions={libraryQuery.data?.items ?? []}
            selectedQuestionIds={selection.selectedQuestionIds}
            onAdd={selection.attach}
            addingQuestionId={selection.attachingQuestionId}
            isLoading={libraryQuery.isPending}
            filters={filters}
            onFiltersChange={setFilters}
          />
        </section>
      </div>

      <div className="mt-8 flex justify-end">
        <Link to={`/quizzes/${quizId}/review`}>
          <Button>Next: Review</Button>
        </Link>
      </div>
    </PageContainer>
  );
}
