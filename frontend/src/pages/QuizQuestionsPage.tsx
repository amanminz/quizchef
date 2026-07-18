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
import { ListChecks, Plus } from "lucide-react";
import { useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import type { QuestionLibraryFilters } from "@/api/questionApi";
import { Button } from "@/components/common/Button";
import { EmptyState } from "@/components/common/EmptyState";
import { EntityStatusBadge } from "@/components/common/EntityStatusBadge";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { ProgressStepper } from "@/components/common/ProgressStepper";
import { Spinner } from "@/components/common/Spinner";
import { WorkflowHeader } from "@/components/common/WorkflowHeader";
import { usePermissions } from "@/features/identity/hooks/usePermissions";
import { useQuestionLibrary } from "@/features/questions/hooks/useQuestionLibrary";
import {
  editQuestionPath,
  newQuestionPath,
  type AuthoringNotice
} from "@/features/questions/quizReturn";
import { QuestionPicker } from "@/features/quizzes/components/QuestionPicker";
import { SortableQuestionRow } from "@/features/quizzes/components/SortableQuestionRow";
import { useQuestionSelection } from "@/features/quizzes/hooks/useQuestionSelection";
import { lifecycleStateTone } from "@/features/quizzes/statusTone";
import { AUTHORING_STEPS } from "@/features/quizzes/workflowSteps";

export function QuizQuestionsPage() {
  const { quizId = "" } = useParams<{ quizId: string }>();
  const location = useLocation();
  const { hasPermission } = usePermissions();
  // The one-shot outcome of an authoring round-trip (published-and-added,
  // or the honest partial-success), carried in router state by the editor.
  const notice = (location.state as { notice?: AuthoringNotice } | null)?.notice;
  const [filters, setFilters] = useState<QuestionLibraryFilters>({});

  const selection = useQuestionSelection(quizId);
  const libraryQuery = useQuestionLibrary(filters);
  // Cosmetic gating only (RFC-009) — the backend authorizes every mutation.
  const canAuthor = hasPermission("QUIZ_CREATE");
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

      {notice && (
        <p
          role="status"
          className={
            notice.tone === "warning"
              ? "mb-4 rounded-md border border-primary/30 bg-primary/5 p-3 text-sm"
              : "mb-4 rounded-md border border-success/30 bg-success/5 p-3 text-sm"
          }
        >
          {notice.message}
        </p>
      )}

      <div className="grid gap-8 lg:grid-cols-2">
        <section>
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
            Selected ({selection.selectedQuestionIds.length})
          </h2>
          {selection.selectedQuestionIds.length === 0 ? (
            <EmptyState
              icon={ListChecks}
              title="No questions selected"
              description="Choose a question from the library or create a new one. Selected questions will appear here in quiz order."
            />
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
          <div className="mb-3 flex items-center justify-between gap-2">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">
              Library
            </h2>
            {canAuthor && (
              <Link to={newQuestionPath(quizId)}>
                <Button size="sm">
                  <Plus aria-hidden className="h-4 w-4" />
                  New Question
                </Button>
              </Link>
            )}
          </div>
          <QuestionPicker
            questions={libraryQuery.data?.items ?? []}
            selectedQuestionIds={selection.selectedQuestionIds}
            onAdd={selection.attach}
            addingQuestionId={selection.attachingQuestionId}
            isLoading={libraryQuery.isPending}
            filters={filters}
            onFiltersChange={setFilters}
            editDraftHref={
              canAuthor ? (questionId) => editQuestionPath(questionId, quizId) : undefined
            }
            emptyAction={
              canAuthor ? (
                <Link to={newQuestionPath(quizId)}>
                  <Button>Create Question</Button>
                </Link>
              ) : undefined
            }
          />
        </section>
      </div>

      <div className="mt-8 flex flex-col items-end gap-2">
        {selection.selectedQuestionIds.length === 0 && (
          <p className="text-sm text-muted-foreground">Add at least one question to continue.</p>
        )}
        {selection.selectedQuestionIds.length === 0 ? (
          <Button disabled>Next: Review</Button>
        ) : (
          <Link to={`/quizzes/${quizId}/review`}>
            <Button>Next: Review</Button>
          </Link>
        )}
      </div>
    </PageContainer>
  );
}
