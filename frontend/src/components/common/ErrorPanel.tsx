import { AlertTriangle } from "lucide-react";
import { Button } from "@/components/common/Button";
import { errorMessage } from "@/api/apiError";

export interface ErrorPanelProps {
  title?: string;
  error: unknown;
  onRetry?: () => void;
}

/** The standard way to show a failure, with an optional retry. */
export function ErrorPanel({ title = "Something went wrong", error, onRetry }: ErrorPanelProps) {
  return (
    <div
      role="alert"
      className="flex flex-col items-center rounded-lg border border-destructive/40 bg-destructive/5 px-6 py-10 text-center"
    >
      <AlertTriangle aria-hidden className="mb-3 h-8 w-8 text-destructive" />
      <h2 className="text-lg font-semibold">{title}</h2>
      <p className="mt-1 max-w-md text-sm text-muted-foreground">{errorMessage(error)}</p>
      {onRetry && (
        <Button variant="secondary" className="mt-6" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}
