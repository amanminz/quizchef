import { Users } from "lucide-react";

/** The lobby before anyone joins — share the code and wait. */
export function WaitingState() {
  return (
    <div className="flex flex-col items-center rounded-lg border border-dashed px-6 py-12 text-center">
      <Users aria-hidden className="mb-3 h-8 w-8 text-muted-foreground" />
      <p className="text-sm font-medium">Waiting for participants…</p>
      <p className="mt-1 max-w-xs text-sm text-muted-foreground">
        Share the session code so players can join from the play page.
      </p>
    </div>
  );
}
