import { ArchiveRestore, Check, Pencil, Trash2 } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { Button } from "@/components/common/Button";
import { Card, CardContent } from "@/components/common/Card";
import { ConfirmDialog } from "@/components/common/Dialog";
import { EntityStatusBadge } from "@/components/common/EntityStatusBadge";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { SectionHeader } from "@/components/common/SectionHeader";
import { Spinner } from "@/components/common/Spinner";
import { languageLabel } from "@/features/gameplay/eventLanguages";
import { QUESTION_TYPES } from "@/features/questions/editorForm";
import { useQuestion } from "@/features/questions/hooks/useQuestion";
import { useQuestionAuthoring } from "@/features/questions/hooks/useQuestionAuthoring";
import { useQuestionUsage } from "@/features/questions/hooks/useQuestionUsage";
import { editQuestionPath } from "@/features/questions/quizReturn";
import { DifficultyBadge } from "@/features/quizzes/components/DifficultyBadge";
import { lifecycleStateTone } from "@/features/quizzes/statusTone";
import type { QuestionResponse } from "@/types/api";

/**
 * The question's read-only detail page — the breadcrumb target between
 * the library and the editor, and the home of the lifecycle actions that
 * need a full view of the question: publish and edit for drafts, archive
 * for published, restore for archived, and delete for questions no quiz
 * composes. The delete affordance is framed by the usage read, but the
 * backend enforces the rule transactionally either way.
 */
export function QuestionDetailPage() {
  const { questionId = "" } = useParams<{ questionId: string }>();
  const navigate = useNavigate();
  const questionQuery = useQuestion(questionId);
  const usageQuery = useQuestionUsage(questionId);
  const { publish, isPublishing, archive, isArchiving, restore, isRestoring, deleteQuestion, isDeleting } =
    useQuestionAuthoring();
  const [confirming, setConfirming] = useState<"archive" | "delete" | null>(null);
  const [actionError, setActionError] = useState<unknown>(null);

  if (questionQuery.isPending) {
    return (
      <PageContainer>
        <div className="flex justify-center py-16">
          <Spinner size="lg" className="text-primary" />
        </div>
      </PageContainer>
    );
  }

  const question = questionQuery.data;
  if (!question) {
    return (
      <PageContainer>
        <ErrorPanel error={questionQuery.error ?? new Error("Question not found")} />
      </PageContainer>
    );
  }

  const title =
    question.localizations?.find(
      (localization) => localization.languageCode === question.defaultLanguage
    )?.title ?? "Question";
  const typeLabel =
    QUESTION_TYPES.find((type) => type.value === question.questionType)?.label ??
    question.questionType;
  const quizCount = usageQuery.data?.quizCount;
  const isUsed = quizCount !== undefined && quizCount > 0;

  async function run(action: () => Promise<unknown>) {
    setActionError(null);
    try {
      await action();
    } catch (error) {
      setActionError(error);
    } finally {
      setConfirming(null);
    }
  }

  return (
    <PageContainer className="max-w-3xl">
      <SectionHeader
        title={title}
        description="Questions are authored once and reused across quizzes."
        actions={
          <div className="flex flex-wrap items-center gap-2">
            {question.state === "DRAFT" && (
              <>
                <Link to={editQuestionPath(questionId)}>
                  <Button variant="secondary">
                    <Pencil aria-hidden className="h-4 w-4" />
                    Edit
                  </Button>
                </Link>
                <Button isLoading={isPublishing} onClick={() => void run(() => publish(questionId))}>
                  <Check aria-hidden className="h-4 w-4" />
                  Publish
                </Button>
              </>
            )}
            {question.state === "PUBLISHED" && (
              <Button
                variant="outline"
                isLoading={isArchiving}
                onClick={() => setConfirming("archive")}
              >
                Archive
              </Button>
            )}
            {question.state === "ARCHIVED" && (
              <Button
                isLoading={isRestoring}
                disabled={isRestoring}
                onClick={() => void run(() => restore(questionId))}
              >
                <ArchiveRestore aria-hidden className="h-4 w-4" />
                Restore
              </Button>
            )}
          </div>
        }
      />

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <EntityStatusBadge
          label={question.state ?? "DRAFT"}
          tone={lifecycleStateTone(question.state)}
        />
        <span className="text-sm text-muted-foreground">{typeLabel}</span>
        {question.difficulty && <DifficultyBadge difficulty={question.difficulty} />}
        {question.defaultLanguage && (
          <span className="text-sm text-muted-foreground">
            Default language: {languageLabel(question.defaultLanguage)}
          </span>
        )}
      </div>

      {actionError != null && (
        <div className="mb-4">
          <ErrorPanel error={actionError} />
        </div>
      )}

      <div className="flex flex-col gap-4">
        {question.localizations?.map((localization) => (
          <LocalizationCard
            key={localization.languageCode}
            question={question}
            localization={localization}
          />
        ))}
      </div>

      <div className="mt-6 rounded-md border border-border p-4">
        <h2 className="text-sm font-semibold">Delete this question</h2>
        {isUsed ? (
          <p className="mt-1 text-sm text-muted-foreground">
            This question is used in {quizCount} quiz{quizCount === 1 ? "" : "zes"}. Remove it from
            those quizzes before deleting.
          </p>
        ) : (
          <p className="mt-1 text-sm text-muted-foreground">
            Deleting is permanent. Only questions not used by any quiz can be deleted.
          </p>
        )}
        <Button
          variant="destructive"
          size="sm"
          className="mt-3"
          disabled={isUsed || usageQuery.isPending}
          isLoading={isDeleting}
          onClick={() => setConfirming("delete")}
        >
          <Trash2 aria-hidden className="h-4 w-4" />
          Delete question
        </Button>
      </div>

      <ConfirmDialog
        open={confirming === "archive"}
        title="Archive this question?"
        description="The question becomes unavailable for new quizzes, while published quizzes that already use it keep working. You can restore it later."
        confirmLabel="Archive"
        destructive
        isConfirming={isArchiving}
        onConfirm={() => void run(() => archive(questionId))}
        onCancel={() => setConfirming(null)}
      />

      <ConfirmDialog
        open={confirming === "delete"}
        title={`Delete "${title}"?`}
        description={`This permanently deletes the question "${title}". This cannot be undone.`}
        confirmLabel="Delete question"
        destructive
        isConfirming={isDeleting}
        onConfirm={() =>
          void run(async () => {
            await deleteQuestion(questionId);
            navigate("/questions", {
              state: { notice: { tone: "success", message: `Deleted "${title}".` } }
            });
          })
        }
        onCancel={() => setConfirming(null)}
      />
    </PageContainer>
  );
}

type Localization = NonNullable<QuestionResponse["localizations"]>[number];

function LocalizationCard({
  question,
  localization
}: {
  question: QuestionResponse;
  localization: Localization;
}) {
  const orderedOptions = [...(question.options ?? [])].sort(
    (a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)
  );
  const textByOptionId = new Map(
    (localization.optionTexts ?? []).map((text) => [text.optionId, text.text ?? ""])
  );

  return (
    <Card>
      <CardContent className="flex flex-col gap-3 p-5">
        <div className="flex items-center justify-between gap-2">
          <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            {languageLabel(localization.languageCode ?? "")}
          </span>
          {localization.languageCode === question.defaultLanguage && (
            <span className="text-xs text-muted-foreground">Default</span>
          )}
        </div>
        <p className="text-base font-medium">{localization.prompt}</p>
        <ul className="grid gap-2 sm:grid-cols-2">
          {orderedOptions.map((option) => (
            <li
              key={option.id}
              className={
                option.correct
                  ? "rounded-md border border-success bg-success/5 px-3 py-2 text-sm font-medium"
                  : "rounded-md border border-border px-3 py-2 text-sm"
              }
            >
              {textByOptionId.get(option.id ?? "") ?? ""}
              {option.correct && (
                <span className="ml-2 text-xs font-semibold text-success">Correct</span>
              )}
            </li>
          ))}
        </ul>
        {localization.explanation && (
          <p className="text-sm text-muted-foreground">{localization.explanation}</p>
        )}
      </CardContent>
    </Card>
  );
}
