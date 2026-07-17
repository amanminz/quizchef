import { resolveLocalization } from "@/features/gameplay/resolveLocalization";
import type { CurrentQuestionResponse } from "@/types/api";

export interface QuestionBodyProps {
  question: CurrentQuestionResponse;
  preferredLanguage?: string;
}

/** The question's prompt, in the viewer's preferred language where available. */
export function QuestionBody({ question, preferredLanguage }: QuestionBodyProps) {
  const localization = resolveLocalization(question, preferredLanguage);
  return <p className="text-lg font-semibold leading-snug">{localization?.prompt}</p>;
}
