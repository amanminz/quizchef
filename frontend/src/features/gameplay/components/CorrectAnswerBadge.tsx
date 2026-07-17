import { Check } from "lucide-react";

/** Marks an option as one of the server-revealed correct answers. */
export function CorrectAnswerBadge() {
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-success/15 px-2 py-0.5 text-xs font-semibold text-success">
      <Check aria-hidden className="h-3 w-3" />
      Correct answer
    </span>
  );
}
