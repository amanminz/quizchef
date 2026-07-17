import { AlertTriangle } from "lucide-react";
import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { errorMessage } from "@/api/apiError";
import { Button } from "@/components/common/Button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/common/Card";
import { ConfirmDialog } from "@/components/common/Dialog";
import { EntityStatusBadge } from "@/components/common/EntityStatusBadge";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { ProgressStepper } from "@/components/common/ProgressStepper";
import { Spinner } from "@/components/common/Spinner";
import { WorkflowHeader } from "@/components/common/WorkflowHeader";
import { useArchiveQuiz } from "@/features/quizzes/hooks/useArchiveQuiz";
import { useQuizPublishing } from "@/features/quizzes/hooks/useQuizPublishing";
import { lifecycleStateTone } from "@/features/quizzes/statusTone";
import { AUTHORING_STEPS } from "@/features/quizzes/workflowSteps";

export function QuizReviewPage() {
  const { quizId = "" } = useParams<{ quizId: string }>();
  const navigate = useNavigate();
  const {
    quiz,
    isLoading,
    questionCount,
    difficultyDistribution,
    languages,
    validationWarnings,
    canPublish,
    publish,
    isPublishing,
    publishError
  } = useQuizPublishing(quizId);
  const archiveMutation = useArchiveQuiz();

  const [confirmPublish, setConfirmPublish] = useState(false);
  const [confirmArchive, setConfirmArchive] = useState(false);

  if (isLoading) {
    return (
      <PageContainer>
        <div className="flex justify-center py-16">
          <Spinner size="lg" className="text-primary" />
        </div>
      </PageContainer>
    );
  }

  if (!quiz) {
    return (
      <PageContainer>
        <ErrorPanel error={new Error("Quiz not found")} />
      </PageContainer>
    );
  }

  const defaultLocalization = quiz.localizations?.find(
    (localization) => localization.languageCode === quiz.defaultLanguage
  );

  async function handlePublish() {
    try {
      await publish();
      setConfirmPublish(false);
      navigate("/quizzes");
    } catch {
      setConfirmPublish(false);
    }
  }

  async function handleArchive() {
    try {
      await archiveMutation.mutateAsync(quizId);
      setConfirmArchive(false);
      navigate("/quizzes");
    } catch {
      setConfirmArchive(false);
    }
  }

  return (
    <PageContainer className="max-w-2xl">
      <WorkflowHeader
        title={defaultLocalization?.title ?? "Quiz"}
        backHref={quiz.state === "DRAFT" ? `/quizzes/${quizId}/questions` : "/quizzes"}
        backLabel={quiz.state === "DRAFT" ? "Questions" : "My Quizzes"}
        status={<EntityStatusBadge label={quiz.state ?? "DRAFT"} tone={lifecycleStateTone(quiz.state)} />}
      />
      {quiz.state === "DRAFT" && (
        <ProgressStepper steps={AUTHORING_STEPS} currentKey="review" className="mb-6" />
      )}

      <Card>
        <CardHeader>
          <CardTitle>{defaultLocalization?.title}</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4 text-sm">
          {defaultLocalization?.description && (
            <p className="text-muted-foreground">{defaultLocalization.description}</p>
          )}
          <dl className="grid grid-cols-2 gap-3">
            <div>
              <dt className="text-muted-foreground">Language</dt>
              <dd className="font-medium">{quiz.defaultLanguage?.toUpperCase()}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Questions</dt>
              <dd className="font-medium">{questionCount}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Difficulty</dt>
              <dd className="font-medium">
                {Object.entries(difficultyDistribution)
                  .map(([difficulty, count]) => `${difficulty}: ${count}`)
                  .join(", ") || "—"}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">Languages covered</dt>
              <dd className="font-medium">
                {languages.length > 0 ? languages.map((l) => l.toUpperCase()).join(", ") : "—"}
              </dd>
            </div>
          </dl>

          {validationWarnings.length > 0 && (
            <div className="rounded-md border border-primary/30 bg-primary/5 p-3">
              <div className="mb-1 flex items-center gap-2 text-primary">
                <AlertTriangle aria-hidden className="h-4 w-4" />
                <span className="text-sm font-medium">Before you publish</span>
              </div>
              <ul className="list-inside list-disc text-sm text-muted-foreground">
                {validationWarnings.map((warning) => (
                  <li key={warning}>{warning}</li>
                ))}
              </ul>
            </div>
          )}

          {publishError && (
            <p role="alert" className="text-sm text-destructive">
              {errorMessage(publishError)}
            </p>
          )}

          <div className="flex justify-end gap-2">
            {quiz.state === "DRAFT" && (
              <Button onClick={() => setConfirmPublish(true)} disabled={!canPublish}>
                Publish
              </Button>
            )}
            {quiz.state === "PUBLISHED" && (
              <Button variant="destructive" onClick={() => setConfirmArchive(true)}>
                Archive
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={confirmPublish}
        title="Publish this quiz?"
        description="Participants will be able to join and play once you host a session. Published content is locked to editing, except visibility."
        confirmLabel="Publish"
        isConfirming={isPublishing}
        onConfirm={() => void handlePublish()}
        onCancel={() => setConfirmPublish(false)}
      />
      <ConfirmDialog
        open={confirmArchive}
        title="Archive this quiz?"
        description="Archiving is terminal — the quiz becomes read-only and retired from new sessions, but is never deleted."
        confirmLabel="Archive"
        destructive
        isConfirming={archiveMutation.isPending}
        onConfirm={() => void handleArchive()}
        onCancel={() => setConfirmArchive(false)}
      />
    </PageContainer>
  );
}
