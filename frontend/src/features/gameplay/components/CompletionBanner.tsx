import { PartyPopper } from "lucide-react";

/** The completion headline, announced to assistive tech via its status role. */
export function CompletionBanner() {
  return (
    <div role="status" className="flex flex-col items-center gap-2 py-4 text-center">
      <PartyPopper aria-hidden className="h-8 w-8 text-primary" />
      <h2 className="text-2xl font-bold tracking-tight">Quiz complete!</h2>
    </div>
  );
}
