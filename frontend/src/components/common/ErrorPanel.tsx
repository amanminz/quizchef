import { AlertTriangle } from "lucide-react";
import { Button } from "@/components/common/Button";
import { errorMessage, isApiClientError } from "@/api/apiError";

export interface ErrorPanelProps {
  title?: string;
  error: unknown;
  onRetry?: () => void;
}

/**
 * The standard way to show a failure, with an optional retry. Every
 * `ApiError` now carries a correlation id (Phase 3 PR #2) — shown here in
 * small print so this doubles as the "fatal error dialog" correlation
 * display, since `ErrorBoundary` renders through this same component.
 */
export function ErrorPanel({ title = "Something went wrong", error, onRetry }: ErrorPanelProps) {
  const correlationId = isApiClientError(error) ? error.correlationId : null;

  return (
    <div
      role="alert"
      className="flex flex-col items-center rounded-lg border border-destructive/40 bg-destructive/5 px-6 py-10 text-center"
    >
      <AlertTriangle aria-hidden className="mb-3 h-8 w-8 text-destructive" />
      <h2 className="text-lg font-semibold">{title}</h2>
      <p className="mt-1 max-w-md text-sm text-muted-foreground">{errorMessage(error)}</p>
      {correlationId && (
        <p className="mt-2 text-xs text-muted-foreground/70">Reference: {correlationId}</p>
      )}
      {onRetry && (
        <Button variant="secondary" className="mt-6" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}
