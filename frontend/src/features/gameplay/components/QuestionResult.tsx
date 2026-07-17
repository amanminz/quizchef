import { CheckCircle2, Info, XCircle } from "lucide-react";
import type { AnswerVerdict } from "@/features/gameplay/verdict";
import { cn } from "@/utils/cn";

const content: Record<AnswerVerdict, { icon: typeof CheckCircle2; text: string; classes: string }> = {
  correct: {
    icon: CheckCircle2,
    text: "Correct!",
    classes: "border-success/30 bg-success/5 text-success"
  },
  incorrect: {
    icon: XCircle,
    text: "Not quite.",
    classes: "border-destructive/30 bg-destructive/5 text-destructive"
  },
  unanswered: {
    icon: Info,
    text: "You didn't answer this one.",
    classes: "border-border bg-muted/50 text-muted-foreground"
  }
};

/** The participant's outcome banner on the reveal screen. */
export function QuestionResult({ verdict }: { verdict: AnswerVerdict }) {
  const { icon: Icon, text, classes } = content[verdict];
  return (
    <div
      role="status"
      className={cn("flex items-center gap-3 rounded-md border px-4 py-3 text-sm font-semibold", classes)}
    >
      <Icon aria-hidden className="h-5 w-5 shrink-0" />
      {text}
    </div>
  );
}
