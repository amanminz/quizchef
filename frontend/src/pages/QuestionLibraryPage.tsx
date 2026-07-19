import { BookOpen, Plus } from "lucide-react";
import { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import type { QuestionLibraryFilters } from "@/api/questionApi";
import { Button } from "@/components/common/Button";
import { ConfirmDialog } from "@/components/common/Dialog";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { SectionHeader } from "@/components/common/SectionHeader";
import { Spinner } from "@/components/common/Spinner";
import { QuestionLibraryRow } from "@/features/questions/components/QuestionLibraryRow";
import { useQuestionAuthoring } from "@/features/questions/hooks/useQuestionAuthoring";
import { useQuestionLibrary } from "@/features/questions/hooks/useQuestionLibrary";
import { newQuestionPath, type AuthoringNotice } from "@/features/questions/quizReturn";
import type { Difficulty, QuestionState } from "@/types/api";

const DIFFICULTIES: Difficulty[] = ["EASY", "MEDIUM", "HARD"];
const STATES: QuestionState[] = ["DRAFT", "PUBLISHED", "ARCHIVED"];

/**
 * The standalone question library (RFC-013): hosts who prepare questions
 * before composing quizzes browse, author, publish, and archive here.
 * Quiz composition (attaching) lives on the quiz Questions step — this
 * page manages the reusable resources themselves.
 */
export function QuestionLibraryPage() {
  const location = useLocation();
  const notice = (location.state as { notice?: AuthoringNotice } | null)?.notice;
  const [filters, setFilters] = useState<QuestionLibraryFilters>({});
  const libraryQuery = useQuestionLibrary(filters);
  const { publish, isPublishing, archive, isArchiving, restore, isRestoring } =
    useQuestionAuthoring();
  const [pendingQuestionId, setPendingQuestionId] = useState<string | null>(null);
  const [confirmArchiveId, setConfirmArchiveId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<unknown>(null);

  const hasFilters = Object.values(filters).some((value) => value !== undefined);
  const questions = libraryQuery.data?.items ?? [];

  async function handlePublish(questionId: string) {
    setActionError(null);
    setPendingQuestionId(questionId);
    try {
      await publish(questionId);
    } catch (error) {
      setActionError(error);
    } finally {
      setPendingQuestionId(null);
    }
  }

  async function handleRestore(questionId: string) {
    setActionError(null);
    setPendingQuestionId(questionId);
    try {
      await restore(questionId);
    } catch (error) {
      setActionError(error);
    } finally {
      setPendingQuestionId(null);
    }
  }

  async function handleArchive() {
    if (!confirmArchiveId) {
      return;
    }
    setActionError(null);
    setPendingQuestionId(confirmArchiveId);
    try {
      await archive(confirmArchiveId);
    } catch (error) {
      setActionError(error);
    } finally {
      setPendingQuestionId(null);
      setConfirmArchiveId(null);
    }
  }

  return (
    <PageContainer>
      <SectionHeader
        title="Question Library"
        description="Author once, reuse across quizzes. Publishing freezes a question's content so quizzes can rely on it."
        actions={
          <Link to={newQuestionPath()}>
            <Button>
              <Plus aria-hidden className="h-4 w-4" />
              New Question
            </Button>
          </Link>
        }
      />

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

      <div className="mb-4 flex flex-wrap gap-2">
        <input
          type="search"
          placeholder="Search questions"
          value={filters.search ?? ""}
          onChange={(event) => setFilters({ ...filters, search: event.target.value || undefined })}
          className="h-9 flex-1 min-w-[10rem] rounded-md border border-input bg-background px-3 text-sm"
          aria-label="Search questions"
        />
        <select
          value={filters.state ?? ""}
          onChange={(event) =>
            setFilters({
              ...filters,
              state: (event.target.value || undefined) as QuestionState | undefined
            })
          }
          className="h-9 rounded-md border border-input bg-background px-2 text-sm"
          aria-label="Filter by status"
        >
          <option value="">Any status</option>
          {STATES.map((state) => (
            <option key={state} value={state}>
              {state}
            </option>
          ))}
        </select>
        <select
          value={filters.difficulty ?? ""}
          onChange={(event) =>
            setFilters({
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
            setFilters({ ...filters, language: event.target.value || undefined })
          }
          className="h-9 w-32 rounded-md border border-input bg-background px-3 text-sm"
          aria-label="Filter by language"
        />
      </div>

      {actionError != null && (
        <div className="mb-4">
          <ErrorPanel error={actionError} />
        </div>
      )}

      {libraryQuery.isPending ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" className="text-primary" />
        </div>
      ) : questions.length === 0 ? (
        hasFilters ? (
          <EmptyState
            icon={BookOpen}
            title="No questions match your filters"
            description="Clear or adjust the filters and try again."
            action={
              <Button variant="secondary" onClick={() => setFilters({})}>
                Clear filters
              </Button>
            }
          />
        ) : (
          <EmptyState
            icon={BookOpen}
            title="Your question library is empty"
            description="Questions are authored once and reused across quizzes. Create your first question to get started."
            action={
              <Link to={newQuestionPath()}>
                <Button>Create Question</Button>
              </Link>
            }
          />
        )
      ) : (
        <div className="flex flex-col gap-2">
          {questions.map((question) => (
            <QuestionLibraryRow
              key={question.id}
              question={question}
              onPublish={(questionId) => void handlePublish(questionId)}
              onArchive={setConfirmArchiveId}
              onRestore={(questionId) => void handleRestore(questionId)}
              isPublishing={isPublishing && pendingQuestionId === question.id}
              isArchiving={isArchiving && pendingQuestionId === question.id}
              isRestoring={isRestoring && pendingQuestionId === question.id}
            />
          ))}
        </div>
      )}

      <ConfirmDialog
        open={confirmArchiveId !== null}
        title="Archive this question?"
        description="The question becomes unavailable for new quizzes, while published quizzes that already use it keep working. You can restore it later."
        confirmLabel="Archive"
        destructive
        isConfirming={isArchiving}
        onConfirm={() => void handleArchive()}
        onCancel={() => setConfirmArchiveId(null)}
      />
    </PageContainer>
  );
}
