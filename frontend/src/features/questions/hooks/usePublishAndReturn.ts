import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { quizApi } from "@/api/quizApi";
import { useQuestionAuthoring } from "@/features/questions/hooks/useQuestionAuthoring";
import { quizQuestionsPath, type AuthoringNotice } from "@/features/questions/quizReturn";
import { quizKeys } from "@/features/quizzes/queryKeys";

/**
 * The tail of the authoring journeys (RFC-013): publish a draft, then —
 * when authoring was launched from a quiz — attach it to that quiz and
 * return to its Questions step. The attachment is a real backend mutation
 * and must succeed before the success state is shown; when it fails after
 * a successful publish, the partial success is reported honestly (the
 * question exists in the library, it is just not selected) instead of
 * being papered over. Publish failures are rethrown so each page keeps
 * its own recovery (the create page has just made the draft; the edit
 * page keeps the form on screen).
 */
export function usePublishAndReturn(quizId: string | undefined) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { publish } = useQuestionAuthoring();
  const [isPublishing, setIsPublishing] = useState(false);

  async function publishAndReturn(questionId: string): Promise<void> {
    setIsPublishing(true);
    try {
      await publish(questionId);
      if (!quizId) {
        const notice: AuthoringNotice = { tone: "success", message: "Question published." };
        navigate("/questions", { state: { notice } });
        return;
      }
      try {
        await quizApi.attachQuestion(quizId, questionId);
        await queryClient.invalidateQueries({ queryKey: quizKeys.detail(quizId) });
        const notice: AuthoringNotice = {
          tone: "success",
          message: "Question published and added to this quiz."
        };
        navigate(quizQuestionsPath(quizId), { state: { notice } });
      } catch {
        const notice: AuthoringNotice = {
          tone: "warning",
          message:
            "Question published, but it could not be added to this quiz. " +
            "You can select it from the library and try again."
        };
        navigate(quizQuestionsPath(quizId), { state: { notice } });
      }
    } finally {
      setIsPublishing(false);
    }
  }

  return { publishAndReturn, isPublishing };
}
