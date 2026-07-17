import { Check } from "lucide-react";
import { cn } from "@/utils/cn";

export interface ProgressStep {
  key: string;
  label: string;
}

export interface ProgressStepperProps {
  steps: ProgressStep[];
  currentKey: string;
  className?: string;
}

/**
 * A horizontal step indicator for any linear workflow (quiz authoring
 * today; any future multi-step flow — session setup, onboarding — can
 * reuse it). Steps before the current one are marked complete; the
 * current step is highlighted; later steps are muted. Purely a display of
 * position — it never owns navigation or validation.
 */
export function ProgressStepper({ steps, currentKey, className }: ProgressStepperProps) {
  const currentIndex = steps.findIndex((step) => step.key === currentKey);

  return (
    <ol className={cn("flex items-center gap-2", className)}>
      {steps.map((step, index) => {
        const isComplete = currentIndex >= 0 && index < currentIndex;
        const isCurrent = step.key === currentKey;
        return (
          <li key={step.key} className="flex items-center gap-2">
            {index > 0 && <span aria-hidden className="h-px w-6 bg-border sm:w-10" />}
            <span
              aria-current={isCurrent ? "step" : undefined}
              className={cn(
                "flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium transition-colors",
                isCurrent && "bg-primary text-primary-foreground",
                isComplete && !isCurrent && "bg-success/15 text-success",
                !isCurrent && !isComplete && "bg-muted text-muted-foreground"
              )}
            >
              {isComplete && !isCurrent && <Check aria-hidden className="h-3 w-3" />}
              {step.label}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
