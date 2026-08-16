import { AlertTriangle } from "lucide-react";
import { Button } from "@/components/common/Button";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { Modal } from "@/components/common/Modal";

export interface RemoveQuestionDialogProps {
  open: boolean;
  onClose: () => void;
  /** 1-based position in the effective sequence, for naming the question. */
  questionNumber: number | null | undefined;
  /** True when this is the question in play. */
  isCurrent: boolean;
  /** Accepted answers on the question right now. */
  answeredCount: number;
  /** True when removing this would end the quiz — nothing follows it. */
  isLastRemaining: boolean;
  onConfirmRemove: () => Promise<unknown>;
  /** Offered alongside removal once answers exist — the other way out. */
  onCorrectInstead: () => void;
  isRemoving: boolean;
  error: unknown;
}

/**
 * The confirmation before a question leaves the session — and, once anyone
 * has answered, the moment the host is told what removing it costs.
 *
 * Two shapes, decided by whether answers exist rather than by which button
 * was clicked. With none, this is an ordinary confirm: nothing is lost.
 * With answers on the board, the host is shown the full consequence and
 * offered the alternative — correcting and replaying keeps the question in
 * the quiz, and is usually what they actually want. Both paths cancel the
 * same answers and points; only one of them asks the room the question
 * again.
 */
export function RemoveQuestionDialog({
  open,
  onClose,
  questionNumber,
  isCurrent,
  answeredCount,
  isLastRemaining,
  onConfirmRemove,
  onCorrectInstead,
  isRemoving,
  error
}: RemoveQuestionDialogProps) {
  const hasAnswers = answeredCount > 0;
  const name = questionNumber != null ? `Question ${questionNumber}` : "This question";

  return (
    <Modal open={open} onClose={onClose} title={`Remove ${name.toLowerCase()}?`}>
      <div className="space-y-4">
        {hasAnswers ? (
          <div
            role="alert"
            className="flex gap-2 rounded-md border border-warning/40 bg-warning/10 p-3 text-sm"
          >
            <AlertTriangle aria-hidden className="mt-0.5 h-4 w-4 shrink-0 text-warning" />
            <div className="space-y-1">
              <p className="font-medium">
                {answeredCount === 1
                  ? "One participant has already answered this question."
                  : `${answeredCount} participants have already answered this question.`}
              </p>
              <p className="text-muted-foreground">
                You can either correct and replay it, or remove it from the session. All answers
                and points from this question will be cancelled either way.
              </p>
            </div>
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">
            {name} will be dropped from this session. The remaining questions renumber, so
            players see no gap. Your question library is not changed.
          </p>
        )}

        {isCurrent && !isLastRemaining && (
          <p className="text-sm text-muted-foreground">
            The timer stops and the next question begins its reading period.
          </p>
        )}
        {isCurrent && isLastRemaining && (
          <p className="text-sm text-muted-foreground">
            Nothing follows this question, so removing it finishes the quiz and takes you
            straight to the results ceremony.
          </p>
        )}

        {error != null && <ErrorPanel error={error} />}

        <div className="flex flex-wrap justify-end gap-2">
          <Button variant="outline" onClick={onClose} disabled={isRemoving}>
            Cancel
          </Button>
          {hasAnswers && (
            <Button variant="secondary" onClick={onCorrectInstead} disabled={isRemoving}>
              Correct &amp; replay
            </Button>
          )}
          <Button
            variant="destructive"
            onClick={() => void onConfirmRemove()}
            isLoading={isRemoving}
          >
            {hasAnswers ? "Remove & continue" : "Remove from session"}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
