import { useState } from "react";
import { Button } from "@/components/common/Button";
import { AnswerOption } from "@/features/gameplay/components/AnswerOption";
import { resolveLocalization } from "@/features/gameplay/resolveLocalization";
import type { CurrentQuestionResponse } from "@/types/api";

export interface AnswerGridProps {
  question: CurrentQuestionResponse;
  preferredLanguage?: string;
  /** True once the question is no longer accepting answers (closed, or the local timer ran out). */
  disabled?: boolean;
  onSubmit: (selectedOptionIds: string[]) => void;
  isSubmitting?: boolean;
  /** The host's monitoring view: options shown, nothing selectable, no submit action. */
  readOnly?: boolean;
}

/**
 * The options for one question. Selection is component-local UI state
 * (RFC-009 ownership table) — nothing about "what's currently checked"
 * belongs in a hook or store, only the final submitted answer does.
 * `MULTIPLE_CHOICE` toggles independently; `SINGLE_CHOICE`/`TRUE_FALSE`
 * clear any other selection, mirroring a real single-answer question.
 */
export function AnswerGrid({
  question,
  preferredLanguage,
  disabled,
  onSubmit,
  isSubmitting,
  readOnly
}: AnswerGridProps) {
  const [selected, setSelected] = useState<string[]>([]);
  const localization = resolveLocalization(question, preferredLanguage);
  const options = [...(question.options ?? [])].sort(
    (a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)
  );
  const isMultiSelect = question.questionType === "MULTIPLE_CHOICE";

  const toggle = (optionId: string) => {
    setSelected((previous) => {
      if (isMultiSelect) {
        return previous.includes(optionId)
          ? previous.filter((id) => id !== optionId)
          : [...previous, optionId];
      }
      return previous.includes(optionId) ? [] : [optionId];
    });
  };

  return (
    <div className="flex flex-col gap-3">
      <div
        role={isMultiSelect ? "group" : "radiogroup"}
        aria-label="Answer options"
        className="grid gap-2 sm:grid-cols-2"
      >
        {options.map((option) => {
          const text =
            localization?.optionTexts?.find((entry) => entry.optionId === option.optionId)?.text ??
            "";
          return (
            <AnswerOption
              key={option.optionId}
              text={text}
              selected={!readOnly && selected.includes(option.optionId ?? "")}
              disabled={readOnly || disabled}
              onToggle={() => option.optionId && toggle(option.optionId)}
            />
          );
        })}
      </div>
      {!readOnly && (
        <Button
          onClick={() => onSubmit(selected)}
          disabled={disabled || selected.length === 0}
          isLoading={isSubmitting}
          className="self-start"
        >
          Submit Answer
        </Button>
      )}
    </div>
  );
}
