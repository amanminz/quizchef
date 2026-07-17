import type { ReactNode } from "react";
import { Card, CardContent } from "@/components/common/Card";
import { QuestionBody } from "@/features/gameplay/components/QuestionBody";
import { QuestionHeader } from "@/features/gameplay/components/QuestionHeader";
import type { CurrentQuestionResponse } from "@/types/api";

export interface QuestionCardProps {
  question: CurrentQuestionResponse;
  preferredLanguage?: string;
  /** AnswerGrid for a participant, or a read-only options list for the host. */
  children: ReactNode;
}

/** The question in play: header (position, progress, timer), prompt, and its answer surface. */
export function QuestionCard({ question, preferredLanguage, children }: QuestionCardProps) {
  return (
    <Card>
      <CardContent className="flex flex-col gap-5 p-6">
        <QuestionHeader
          number={question.questionNumber ?? 0}
          total={question.totalQuestions ?? 0}
          endsAt={question.phase === "QUESTION_OPEN" ? question.endsAt : null}
        />
        <QuestionBody question={question} preferredLanguage={preferredLanguage} />
        {children}
      </CardContent>
    </Card>
  );
}
