import { Tv } from "lucide-react";

/**
 * Shown to every participant between the final question completing and
 * the host releasing final standings — backend-enforced (the personal
 * result read refuses until release), not merely hidden with CSS. A
 * refresh or reconnect during this window re-derives the same phase from
 * the session summary (`finalResultsReleased`), so it never flashes a
 * rank that hasn't been released yet.
 */
export function FinalResultsPendingScreen() {
  return (
    <div className="flex flex-col items-center gap-3 rounded-lg border border-dashed px-6 py-16 text-center">
      <Tv aria-hidden className="h-8 w-8 text-muted-foreground" />
      <p className="text-xl font-bold">Quiz complete!</p>
      <p className="max-w-sm text-sm text-muted-foreground">
        The winners are being announced.
        <br />
        Please watch the shared screen.
      </p>
    </div>
  );
}
