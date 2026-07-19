import { useState } from "react";
import { Link, useLocation, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { errorMessage } from "@/api/apiError";
import { Button } from "@/components/common/Button";
import { Card, CardContent } from "@/components/common/Card";
import { EntityStatusBadge } from "@/components/common/EntityStatusBadge";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { SectionHeader } from "@/components/common/SectionHeader";
import { Spinner } from "@/components/common/Spinner";
import { QuestionEditor } from "@/features/questions/components/QuestionEditor";
import {
  toFormValues,
  toUpdateRequest,
  type QuestionEditorValues
} from "@/features/questions/editorForm";
import { usePublishAndReturn } from "@/features/questions/hooks/usePublishAndReturn";
import { useQuestion } from "@/features/questions/hooks/useQuestion";
import { useQuestionAuthoring } from "@/features/questions/hooks/useQuestionAuthoring";
import {
  quizIdFromSearch,
  quizQuestionsPath,
  type AuthoringNotice
} from "@/features/questions/quizReturn";
import { lifecycleStateTone } from "@/features/quizzes/statusTone";

/**
 * Editing an existing question. Drafts are fully editable; published and
 * archived questions are immutable by design (RFC-003) — this page states
 * that instead of offering a dead-end form.
 */
export function EditQuestionPage() {
  const { questionId = "" } = useParams<{ questionId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const quizId = quizIdFromSearch(searchParams);

  const questionQuery = useQuestion(questionId);
  const { update, isUpdating } = useQuestionAuthoring();
  const { publishAndReturn, isPublishing } = usePublishAndReturn(quizId);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [action, setAction] = useState<"draft" | "publish" | null>(null);
  // A one-shot arrival notice ("Draft saved", "publishing failed") from the create page.
  const notice = (location.state as { notice?: AuthoringNotice } | null)?.notice;
  const [savedNotice, setSavedNotice] = useState<string | null>(null);

  const backHref = quizId ? quizQuestionsPath(quizId) : "/questions";
  const backLabel = quizId ? "Back to quiz questions" : "Back to Question Library";

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

  async function handleSaveDraft(values: QuestionEditorValues) {
    setAction("draft");
    setSubmitError(null);
    setSavedNotice(null);
    try {
      await update({ questionId, request: toUpdateRequest(values, question!) });
      setSavedNotice("Draft saved.");
    } catch (error) {
      setSubmitError(errorMessage(error));
    }
  }

  async function handlePublish(values: QuestionEditorValues) {
    setAction("publish");
    setSubmitError(null);
    setSavedNotice(null);
    try {
      await update({ questionId, request: toUpdateRequest(values, question!) });
      await publishAndReturn(questionId);
    } catch (error) {
      setSubmitError(errorMessage(error));
    }
  }

  const title =
    question.localizations?.find(
      (localization) => localization.languageCode === question.defaultLanguage
    )?.title ?? "Question";

  if (question.state !== "DRAFT") {
    return (
      <PageContainer className="max-w-2xl">
        <SectionHeader
          title={title}
          description="Published questions are immutable — quizzes may already rely on them. Archive this question and author a new one to change its content."
        />
        <div className="mb-4">
          <EntityStatusBadge
            label={question.state ?? "PUBLISHED"}
            tone={lifecycleStateTone(question.state)}
          />
        </div>
        <Link to={backHref}>
          <Button variant="secondary">{backLabel}</Button>
        </Link>
      </PageContainer>
    );
  }

  return (
    <PageContainer className="max-w-2xl">
      <SectionHeader
        title={`Edit: ${title}`}
        description="This question is a draft and stays editable. Publishing freezes its content so quizzes can rely on it."
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
      {savedNotice && (
        <p
          role="status"
          className="mb-4 rounded-md border border-success/30 bg-success/5 p-3 text-sm"
        >
          {savedNotice}
        </p>
      )}
      <Card>
        <CardContent className="pt-6">
          <QuestionEditor
            key={question.version}
            initialValues={toFormValues(question)}
            onSaveDraft={handleSaveDraft}
            onPublish={handlePublish}
            onCancel={() => navigate(backHref)}
            isSavingDraft={action === "draft" && isUpdating}
            isPublishing={action === "publish" && (isUpdating || isPublishing)}
            submitError={submitError}
          />
        </CardContent>
      </Card>
    </PageContainer>
  );
}
