import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { errorMessage } from "@/api/apiError";
import { Card, CardContent } from "@/components/common/Card";
import { PageContainer } from "@/components/common/PageContainer";
import { SectionHeader } from "@/components/common/SectionHeader";
import { QuestionEditor } from "@/features/questions/components/QuestionEditor";
import { toCreateRequest, type QuestionEditorValues } from "@/features/questions/editorForm";
import { usePublishAndReturn } from "@/features/questions/hooks/usePublishAndReturn";
import { useQuestionAuthoring } from "@/features/questions/hooks/useQuestionAuthoring";
import {
  editQuestionPath,
  quizIdFromSearch,
  quizQuestionsPath,
  type AuthoringNotice
} from "@/features/questions/quizReturn";

/**
 * Authoring a new question — standalone from the library, or from a
 * quiz's Questions step (?quizId=…), in which case publishing attaches
 * the question to that quiz and returns there (RFC-013).
 */
export function CreateQuestionPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const quizId = quizIdFromSearch(searchParams);

  const { create, isCreating } = useQuestionAuthoring();
  const { publishAndReturn, isPublishing } = usePublishAndReturn(quizId);
  const [submitError, setSubmitError] = useState<string | null>(null);
  // Which button is in flight — both run create, only one should spin.
  const [action, setAction] = useState<"draft" | "publish" | null>(null);

  async function handleSaveDraft(values: QuestionEditorValues) {
    setAction("draft");
    setSubmitError(null);
    try {
      const question = await create(toCreateRequest(values));
      const notice: AuthoringNotice = {
        tone: "success",
        message: "Draft saved. Keep editing, or publish it when it's ready."
      };
      navigate(editQuestionPath(question.id ?? "", quizId), { replace: true, state: { notice } });
    } catch (error) {
      setSubmitError(errorMessage(error));
    }
  }

  async function handlePublish(values: QuestionEditorValues) {
    setAction("publish");
    setSubmitError(null);
    let questionId: string;
    try {
      const question = await create(toCreateRequest(values));
      questionId = question.id ?? "";
    } catch (error) {
      setSubmitError(errorMessage(error));
      return;
    }
    try {
      await publishAndReturn(questionId);
    } catch (error) {
      // The draft exists — recover in the editor for that draft, honestly.
      const notice: AuthoringNotice = {
        tone: "warning",
        message: `Question was saved as a draft, but publishing failed: ${errorMessage(error)}`
      };
      navigate(editQuestionPath(questionId, quizId), { replace: true, state: { notice } });
    }
  }

  return (
    <PageContainer className="max-w-2xl">
      <SectionHeader
        title="New Question"
        description={
          quizId
            ? "Publishing will add this question to your quiz."
            : "Questions are authored once and reused across quizzes."
        }
      />
      <Card>
        <CardContent className="pt-6">
          <QuestionEditor
            onSaveDraft={handleSaveDraft}
            onPublish={handlePublish}
            onCancel={() => navigate(quizId ? quizQuestionsPath(quizId) : "/questions")}
            isSavingDraft={action === "draft" && isCreating}
            isPublishing={action === "publish" && (isCreating || isPublishing)}
            submitError={submitError}
          />
        </CardContent>
      </Card>
    </PageContainer>
  );
}
