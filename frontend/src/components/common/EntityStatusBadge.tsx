import type { ComponentProps } from "react";
import { cn } from "@/utils/cn";

export type EntityStatusTone = "neutral" | "positive" | "warning" | "critical";

const toneClasses: Record<EntityStatusTone, string> = {
  neutral: "bg-muted text-muted-foreground",
  positive: "bg-success/15 text-success",
  warning: "bg-primary/15 text-primary",
  critical: "bg-destructive/15 text-destructive"
};

export interface EntityStatusBadgeProps extends ComponentProps<"span"> {
  label: string;
  tone: EntityStatusTone;
}

/**
 * A lifecycle-state pill for any entity (quiz, question, session, ...) —
 * generic by design: it renders a label and a tone, never a domain enum.
 * Callers map their own state to a tone (see quizStatusTone in the quizzes
 * feature) so this component has no knowledge of any particular workflow.
 */
export function EntityStatusBadge({ label, tone, className, ...props }: EntityStatusBadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium",
        toneClasses[tone],
        className
      )}
      {...props}
    >
      {label}
    </span>
  );
}
