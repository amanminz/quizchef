import type { ReactNode } from "react";
import { cn } from "@/utils/cn";

export interface PresentationMetricProps {
  label: string;
  value: ReactNode;
  icon?: ReactNode;
  tone?: "default" | "warning" | "critical" | "success";
  /** "timer" for a live countdown, "status" for a plain count. */
  role?: "timer" | "status";
  "aria-label"?: string;
}

/**
 * A compact, equal-weight metric box for Presentation Mode's status row —
 * "Answered 7 / 10" and "Time left 18" share this exact primitive so their
 * label size, numeral size, and box dimensions are guaranteed identical
 * (the projector-layout hotfix's explicit requirement), not just visually
 * similar. A fixed minimum width keeps the row from reflowing as the
 * answered count or the digit count changes; tabular numerals do the same
 * within the value itself.
 */
export function PresentationMetric({
  label,
  value,
  icon,
  tone = "default",
  role = "status",
  "aria-label": ariaLabel
}: PresentationMetricProps) {
  return (
    <div
      role={role}
      aria-label={ariaLabel}
      className={cn(
        "flex min-w-[6.5rem] flex-col items-center gap-0.5 rounded-xl border-2 px-3 py-1.5 sm:px-4 sm:py-2",
        tone === "default" && "border-border text-foreground",
        tone === "success" && "border-success bg-success/10 text-success",
        tone === "warning" &&
          "border-amber-500 text-amber-600 dark:border-amber-400 dark:text-amber-400",
        tone === "critical" && "border-destructive text-destructive motion-safe:animate-pulse"
      )}
    >
      <span className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-[0.15em] sm:text-sm">
        {icon}
        {label}
      </span>
      <span
        className="min-w-[2.5ch] text-center font-mono font-black leading-none tabular-nums"
        style={{ fontSize: "clamp(1.5rem, 3.5vw, 2.75rem)" }}
      >
        {value}
      </span>
    </div>
  );
}
