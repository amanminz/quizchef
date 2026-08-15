import { AlertTriangle, Check } from "lucide-react";
import { useMemo, useState } from "react";
import { Button } from "@/components/common/Button";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { Modal } from "@/components/common/Modal";
import { EVENT_LANGUAGES, languageLabel } from "@/features/gameplay/eventLanguages";
import type { CorrectQuestionRequest, SessionQuestionDto } from "@/types/api";

export interface QuestionCorrectionDialogProps {
  open: boolean;
  onClose: () => void;
  question: SessionQuestionDto;
  /** True when this is the question in play — correcting it will replay it. */
  isCurrent: boolean;
  /** Accepted answers on the question right now; drives the replay warning. */
  answeredCount: number;
  onSubmit: (request: CorrectQuestionRequest) => Promise<unknown>;
  isSubmitting: boolean;
  error: unknown;
}

interface DraftLocalization {
  languageCode: string;
  prompt: string;
  optionTexts: Record<string, string>;
}

/**
 * The host's mid-session fix for a bad question: its wording in each
 * language, and which options are actually correct.
 *
 * Scoped to what a live correction can safely change. Options are reworded
 * and re-marked but never added or removed — answers already recorded point
 * at these ids, and a changed option set would make the cancelled attempt
 * and the replayed one incomparable. Adding an option is an authoring
 * decision, and authoring happens in the Question Library, not mid-event.
 *
 * The dialog is the only place in the host's live UI that shows an
 * unrevealed answer key. That is unavoidable — a wrong key cannot be fixed
 * unseen — so it is kept behind an explicit open rather than shown in the
 * panel's rows, where a projected screen would carry it to the room.
 */
export function QuestionCorrectionDialog({
  open,
  onClose,
  question,
  isCurrent,
  answeredCount,
  onSubmit,
  isSubmitting,
  error
}: QuestionCorrectionDialogProps) {
  const options = useMemo(
    () => [...(question.options ?? [])].sort((a, b) => a.displayOrder - b.displayOrder),
    [question.options]
  );

  // Every language the event offers, seeded from what the question already
  // says. A language the question was never authored in starts blank and is
  // simply left out of the request unless the host writes something.
  const [drafts, setDrafts] = useState<DraftLocalization[]>(() =>
    EVENT_LANGUAGES.map((language) => {
      const authored = (question.localizations ?? []).find(
        (localization) => localization.languageCode === language.value
      );
      return {
        languageCode: language.value,
        prompt: authored?.prompt ?? "",
        optionTexts: Object.fromEntries(
          options.map((option) => [
            option.optionId,
            (authored?.optionTexts ?? []).find((text) => text.optionId === option.optionId)?.text ??
              ""
          ])
        )
      };
    })
  );
  const [correctOptionIds, setCorrectOptionIds] = useState<string[]>(
    () => question.correctOptionIds ?? []
  );

  const setPrompt = (languageCode: string, prompt: string) =>
    setDrafts((current) =>
      current.map((draft) => (draft.languageCode === languageCode ? { ...draft, prompt } : draft))
    );

  const setOptionText = (languageCode: string, optionId: string, text: string) =>
    setDrafts((current) =>
      current.map((draft) =>
        draft.languageCode === languageCode
          ? { ...draft, optionTexts: { ...draft.optionTexts, [optionId]: text } }
          : draft
      )
    );

  const toggleCorrect = (optionId: string) =>
    setCorrectOptionIds((current) =>
      current.includes(optionId)
        ? current.filter((id) => id !== optionId)
        : // SINGLE_CHOICE is the common case, but the server accepts any
          // non-empty set and the question type decides what is valid —
          // this only collects the host's intent.
          [...current, optionId]
    );

  const written = drafts.filter((draft) => draft.prompt.trim().length > 0);
  const canSubmit = written.length > 0 && correctOptionIds.length > 0 && !isSubmitting;

  const submit = async () => {
    if (!canSubmit) {
      return;
    }
    await onSubmit({
      correctOptionIds,
      localizations: written.map((draft) => ({
        languageCode: draft.languageCode,
        prompt: draft.prompt.trim(),
        options: options
          .filter((option) => (draft.optionTexts[option.optionId] ?? "").trim().length > 0)
          .map((option) => ({
            optionId: option.optionId,
            text: draft.optionTexts[option.optionId]!.trim()
          }))
      }))
    });
    onClose();
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={isCurrent ? "Correct and replay this question" : "Correct this question"}
      className="max-w-2xl"
    >
      <div className="space-y-4">
        <p className="text-sm text-muted-foreground">
          The correction applies to this session only. The question in your library is not
          changed, and other quizzes using it are unaffected.
        </p>

        {isCurrent && (
          <div
            role="status"
            className="flex gap-2 rounded-md border border-warning/40 bg-warning/10 p-3 text-sm"
          >
            <AlertTriangle aria-hidden className="mt-0.5 h-4 w-4 shrink-0 text-warning" />
            <div>
              <p className="font-medium">This question is on screen now.</p>
              <p className="text-muted-foreground">
                {answeredCount > 0
                  ? `Saving will cancel all ${answeredCount} answer${
                      answeredCount === 1 ? "" : "s"
                    } and the points they earned, then replay the corrected question from its reading period.`
                  : "Saving will replay the corrected question from its reading period."}
              </p>
            </div>
          </div>
        )}

        <fieldset className="space-y-2">
          <legend className="text-sm font-medium">Correct answer</legend>
          <div className="space-y-1">
            {options.map((option, index) => {
              const selected = correctOptionIds.includes(option.optionId);
              return (
                <label
                  key={option.optionId}
                  className={`flex cursor-pointer items-center gap-2 rounded-md border px-3 py-2 text-sm ${
                    selected ? "border-success bg-success/10" : "border-input"
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={selected}
                    onChange={() => toggleCorrect(option.optionId)}
                    className="h-4 w-4"
                  />
                  <span className="font-medium">{String.fromCharCode(65 + index)}</span>
                  <span className="text-muted-foreground">
                    {/* Whichever language the host has written this option
                        in — the answer key is one choice across all of
                        them, so any wording that identifies it will do. */}
                    {drafts.find((draft) => draft.optionTexts[option.optionId]?.trim())
                      ?.optionTexts[option.optionId] ?? "(no text)"}
                  </span>
                  {selected && <Check aria-hidden className="ml-auto h-4 w-4 text-success" />}
                </label>
              );
            })}
          </div>
        </fieldset>

        {drafts.map((draft) => (
          <fieldset key={draft.languageCode} className="space-y-2 rounded-md border p-3">
            <legend className="px-1 text-sm font-medium">
              {languageLabel(draft.languageCode)}
            </legend>
            <label className="block space-y-1">
              <span className="text-xs font-medium text-muted-foreground">Question</span>
              <textarea
                value={draft.prompt}
                onChange={(event) => setPrompt(draft.languageCode, event.target.value)}
                rows={2}
                className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
              />
            </label>
            {options.map((option, index) => (
              <label key={option.optionId} className="block space-y-1">
                <span className="text-xs font-medium text-muted-foreground">
                  Option {String.fromCharCode(65 + index)}
                </span>
                <input
                  value={draft.optionTexts[option.optionId] ?? ""}
                  onChange={(event) =>
                    setOptionText(draft.languageCode, option.optionId, event.target.value)
                  }
                  className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                />
              </label>
            ))}
          </fieldset>
        ))}

        {error != null && <ErrorPanel error={error} />}

        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button onClick={() => void submit()} disabled={!canSubmit} isLoading={isSubmitting}>
            {isCurrent ? "Save and replay" : "Save correction"}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
