import { Sparkles } from "lucide-react";

/**
 * Shown between "session started" and the first question opening. There
 * is no server-issued countdown duration (no such setting exists in
 * `SessionSettingsDto`), so this is an honest indeterminate "get ready"
 * animation rather than a fabricated numeric countdown — it ends the
 * instant `question.started` arrives, driven by that event alone.
 */
export function CountdownOverlay() {
  return (
    <div
      role="status"
      className="flex flex-col items-center gap-3 rounded-lg border border-dashed px-6 py-16 text-center"
    >
      <Sparkles aria-hidden className="h-8 w-8 animate-pulse text-primary" />
      <p className="text-lg font-semibold">Get ready!</p>
      <p className="text-sm text-muted-foreground">The first question is about to start.</p>
    </div>
  );
}
