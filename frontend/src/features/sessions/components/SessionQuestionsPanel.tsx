import { ChevronDown, ChevronRight, ListChecks, Pencil, Trash2 } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/common/Button";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { Spinner } from "@/components/common/Spinner";
import { EVENT_LANGUAGES } from "@/features/gameplay/eventLanguages";
import { QuestionCorrectionDialog } from "@/features/sessions/components/QuestionCorrectionDialog";
import { RemoveQuestionDialog } from "@/features/sessions/components/RemoveQuestionDialog";
import { useLiveQuestionRecovery } from "@/features/sessions/hooks/useLiveQuestionRecovery";
import { useSessionQuestions } from "@/features/sessions/hooks/useSessionQuestions";
import type { SessionQuestionDto } from "@/types/api";
import { cn } from "@/utils/cn";

export interface SessionQuestionsPanelProps {
  sessionId: string | undefined;
  /** Accepted answers on the question in play — drives the removal warning. */
  answeredCount: number;
}

const STATUS_LABEL: Record<string, string> = {
  PLAYED: "Played",
  CURRENT: "On screen",
  UPCOMING: "Upcoming",
  REMOVED: "Removed"
};

/** The prompt to show in a row: the first event language the question has. */
function rowPrompt(question: SessionQuestionDto): string {
  const localizations = question.localizations ?? [];
  for (const language of EVENT_LANGUAGES) {
    const match = localizations.find(
      (localization) => localization.languageCode === language.value
    );
    if (match?.prompt) {
      return match.prompt;
    }
  }
  return localizations[0]?.prompt ?? "(untitled question)";
}

/**
 * The host's working view of the session's questions, and the only place
 * they can act on one that is not currently on screen.
 *
 * Collapsed by default and never rendered in presentation mode. Both are
 * the same precaution: the underlying read carries answer keys for
 * questions the room has not reached, so nothing here may be one careless
 * projector-share away from spoiling the quiz. Rows deliberately show the
 * prompt only — the key appears solely inside the correction dialog, which
 * the host has to open on purpose.
 *
 * A played question offers nothing. Its answers are scored and its
 * standings shown, and quietly rescoring or dropping it would change a
 * leaderboard the room has already seen; the server refuses either way, and
 * showing buttons that 409 would be worse than showing none.
 */
export function SessionQuestionsPanel({ sessionId, answeredCount }: SessionQuestionsPanelProps) {
  const [expanded, setExpanded] = useState(false);
  const [correcting, setCorrecting] = useState<SessionQuestionDto | null>(null);
  const [removing, setRemoving] = useState<SessionQuestionDto | null>(null);
  const questionsQuery = useSessionQuestions(sessionId, expanded);
  const recovery = useLiveQuestionRecovery(sessionId);

  const questions = questionsQuery.data?.questions ?? [];
  const total = questionsQuery.data?.totalQuestions ?? 0;
  const playable = questions.filter((question) => question.status !== "REMOVED");
  const currentIndex = playable.findIndex((question) => question.status === "CURRENT");
  const isLastRemaining = currentIndex >= 0 && currentIndex === playable.length - 1;

  return (
    <section className="mb-4 rounded-lg border bg-card">
      <button
        type="button"
        onClick={() => setExpanded((open) => !open)}
        aria-expanded={expanded}
        className="flex w-full items-center gap-2 px-4 py-3 text-left text-sm font-medium"
      >
        {expanded ? (
          <ChevronDown aria-hidden className="h-4 w-4" />
        ) : (
          <ChevronRight aria-hidden className="h-4 w-4" />
        )}
        <ListChecks aria-hidden className="h-4 w-4 text-muted-foreground" />
        Session questions
        {expanded && total > 0 && (
          <span className="ml-auto text-xs font-normal text-muted-foreground">
            {total} in play
          </span>
        )}
      </button>

      {expanded && (
        <div className="border-t px-4 py-3">
          {questionsQuery.isPending && (
            <div className="flex justify-center py-6">
              <Spinner />
            </div>
          )}
          {questionsQuery.error != null && (
            <ErrorPanel
              error={questionsQuery.error}
              onRetry={() => void questionsQuery.refetch()}
            />
          )}

          <ul className="space-y-1">
            {questions.map((question) => {
              const removed = question.status === "REMOVED";
              const played = question.status === "PLAYED";
              return (
                <li
                  key={question.questionId}
                  className={cn(
                    "flex items-center gap-3 rounded-md px-2 py-2 text-sm",
                    question.status === "CURRENT" && "bg-primary/10",
                    removed && "opacity-60"
                  )}
                >
                  <span className="w-8 shrink-0 text-right font-medium tabular-nums">
                    {question.questionNumber ?? "—"}
                  </span>
                  <span className={cn("min-w-0 flex-1 truncate", removed && "line-through")}>
                    {rowPrompt(question)}
                  </span>
                  {question.corrected && !removed && (
                    <span className="shrink-0 rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground">
                      Corrected
                    </span>
                  )}
                  <span className="w-20 shrink-0 text-xs text-muted-foreground">
                    {STATUS_LABEL[question.status ?? ""] ?? question.status}
                  </span>
                  {!removed && !played && (
                    <span className="flex shrink-0 gap-1">
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label={`Edit question ${question.questionNumber}`}
                        onClick={() => setCorrecting(question)}
                      >
                        <Pencil aria-hidden className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        aria-label={`Remove question ${question.questionNumber}`}
                        onClick={() => setRemoving(question)}
                      >
                        <Trash2 aria-hidden className="h-4 w-4 text-destructive" />
                      </Button>
                    </span>
                  )}
                </li>
              );
            })}
          </ul>
        </div>
      )}

      {correcting && (
        <QuestionCorrectionDialog
          open
          onClose={() => setCorrecting(null)}
          // Remounted per question: the dialog seeds its draft from the
          // question it opened on, and a shared instance would carry one
          // question's edits into the next.
          key={correcting.questionId}
          question={correcting}
          isCurrent={correcting.status === "CURRENT"}
          answeredCount={correcting.status === "CURRENT" ? answeredCount : 0}
          onSubmit={(request) => recovery.correctQuestion(correcting.questionId, request)}
          isSubmitting={recovery.isCorrecting}
          error={recovery.correctError}
        />
      )}

      {removing && (
        <RemoveQuestionDialog
          open
          onClose={() => setRemoving(null)}
          questionNumber={removing.questionNumber}
          isCurrent={removing.status === "CURRENT"}
          answeredCount={removing.status === "CURRENT" ? answeredCount : 0}
          isLastRemaining={removing.status === "CURRENT" && isLastRemaining}
          onConfirmRemove={async () => {
            await recovery.removeQuestion(removing.questionId);
            setRemoving(null);
          }}
          onCorrectInstead={() => {
            setCorrecting(removing);
            setRemoving(null);
          }}
          isRemoving={recovery.isRemoving}
          error={recovery.removeError}
        />
      )}
    </section>
  );
}
