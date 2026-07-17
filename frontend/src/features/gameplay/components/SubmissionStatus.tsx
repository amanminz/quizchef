import { CheckCircle2 } from "lucide-react";

/** Replaces the answer grid once a submission is recorded — confirmation only, never correctness. */
export function SubmissionStatus() {
  return (
    <div
      role="status"
      className="flex items-center gap-3 rounded-md border border-success/30 bg-success/5 px-4 py-3 text-sm font-medium text-success"
    >
      <CheckCircle2 aria-hidden className="h-5 w-5 shrink-0" />
      Answer submitted — waiting for the question to close.
    </div>
  );
}
