import { Plus, Trash2 } from "lucide-react";
import { useFieldArray, useForm } from "react-hook-form";
import { Button } from "@/components/common/Button";
import { FormField } from "@/components/forms/FormField";
import {
  emptyEditorValues,
  QUESTION_TYPES,
  questionEditorSchema,
  trueFalseOptions,
  type QuestionEditorValues
} from "@/features/questions/editorForm";
import type { QuestionType } from "@/types/api";
import { zodForm } from "@/utils/validation";

const LANGUAGES = [
  { value: "en", label: "English" },
  { value: "kn", label: "Kannada" },
  { value: "hi", label: "Hindi" },
  { value: "ta", label: "Tamil" },
  { value: "te", label: "Telugu" },
  { value: "ml", label: "Malayalam" }
];

const DIFFICULTIES = ["EASY", "MEDIUM", "HARD"] as const;

export interface QuestionEditorProps {
  initialValues?: QuestionEditorValues;
  /** Editing an existing draft: type and default language are fixed at creation. */
  structureLocked?: boolean;
  onSaveDraft: (values: QuestionEditorValues) => void | Promise<void>;
  onPublish: (values: QuestionEditorValues) => void | Promise<void>;
  onCancel: () => void;
  isSavingDraft?: boolean;
  isPublishing?: boolean;
  /** Page-level failure from the submit handlers (field errors render inline). */
  submitError?: string | null;
}

/**
 * The one question editor (RFC-013): used by both the create and edit
 * pages, whether authoring standalone or from inside a quiz. Purely a
 * form — the pages own the mutations, navigation, and return-to-quiz
 * behavior. Validation mirrors the backend's structural rules per question
 * type for fast feedback; the server remains the authority for both Save
 * draft and Publish (a draft must already be structurally valid).
 */
export function QuestionEditor({
  initialValues,
  structureLocked = false,
  onSaveDraft,
  onPublish,
  onCancel,
  isSavingDraft = false,
  isPublishing = false,
  submitError
}: QuestionEditorProps) {
  const {
    register,
    handleSubmit,
    watch,
    setValue,
    control,
    formState: { errors, isDirty }
  } = useForm<QuestionEditorValues>(
    zodForm(questionEditorSchema, { defaultValues: initialValues ?? emptyEditorValues() })
  );
  const { fields, append, remove, replace } = useFieldArray({ control, name: "options" });

  const questionType = watch("questionType");
  const options = watch("options");
  const exclusiveCorrect = questionType !== "MULTIPLE_CHOICE";

  function handleTypeChange(nextType: QuestionType) {
    setValue("questionType", nextType, { shouldDirty: true });
    if (nextType === "TRUE_FALSE") {
      replace(trueFalseOptions());
    } else if (nextType === "SINGLE_CHOICE") {
      // Keep the options but collapse to a single correct one.
      const firstCorrect = options.findIndex((option) => option.correct);
      options.forEach((_, index) =>
        setValue(`options.${index}.correct`, index === Math.max(firstCorrect, 0))
      );
    }
  }

  function markCorrect(index: number, correct: boolean) {
    if (exclusiveCorrect) {
      options.forEach((_, other) => setValue(`options.${other}.correct`, other === index));
    } else {
      setValue(`options.${index}.correct`, correct);
    }
  }

  function confirmCancel() {
    if (!isDirty || window.confirm("Discard unsaved changes?")) {
      onCancel();
    }
  }

  return (
    <form
      onSubmit={handleSubmit((values) => onPublish(values))}
      noValidate
      className="flex flex-col gap-4"
    >
      <FormField label="Title" error={errors.title?.message} {...register("title")} />

      <div className="flex flex-col gap-1.5">
        <label htmlFor="question-prompt" className="text-sm font-medium">
          Prompt
        </label>
        <textarea
          id="question-prompt"
          rows={3}
          aria-invalid={errors.prompt ? true : undefined}
          className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          {...register("prompt")}
        />
        {errors.prompt && (
          <p role="alert" className="text-sm text-destructive">
            {errors.prompt.message}
          </p>
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="question-type" className="text-sm font-medium">
            Question type
          </label>
          <select
            id="question-type"
            value={questionType}
            disabled={structureLocked}
            onChange={(event) => handleTypeChange(event.target.value as QuestionType)}
            className="h-10 rounded-md border border-input bg-background px-3 text-sm disabled:opacity-50"
          >
            {QUESTION_TYPES.map((type) => (
              <option key={type.value} value={type.value}>
                {type.label}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="question-difficulty" className="text-sm font-medium">
            Difficulty
          </label>
          <select
            id="question-difficulty"
            className="h-10 rounded-md border border-input bg-background px-3 text-sm"
            {...register("difficulty")}
          >
            {DIFFICULTIES.map((difficulty) => (
              <option key={difficulty} value={difficulty}>
                {difficulty}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="question-language" className="text-sm font-medium">
            Language
          </label>
          <select
            id="question-language"
            disabled={structureLocked}
            className="h-10 rounded-md border border-input bg-background px-3 text-sm disabled:opacity-50"
            {...register("defaultLanguage")}
          >
            {LANGUAGES.map((language) => (
              <option key={language.value} value={language.value}>
                {language.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1 text-sm font-medium">
          Options
          <span className="ml-2 font-normal text-muted-foreground">
            {exclusiveCorrect ? "Mark the correct answer" : "Mark every correct answer"}
          </span>
        </legend>
        {fields.map((field, index) => (
          <div key={field.id} className="flex items-start gap-2">
            <input
              type={exclusiveCorrect ? "radio" : "checkbox"}
              name={exclusiveCorrect ? "correct-option" : undefined}
              checked={options[index]?.correct ?? false}
              onChange={(event) => markCorrect(index, event.target.checked)}
              aria-label={`Option ${index + 1} is correct`}
              className="mt-3 h-4 w-4 accent-primary"
            />
            <div className="flex-1">
              <input
                aria-label={`Option ${index + 1} text`}
                aria-invalid={errors.options?.[index]?.text ? true : undefined}
                placeholder={`Option ${index + 1}`}
                className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                {...register(`options.${index}.text`)}
              />
              {errors.options?.[index]?.text && (
                <p role="alert" className="mt-1 text-sm text-destructive">
                  {errors.options[index]?.text?.message}
                </p>
              )}
            </div>
            {questionType !== "TRUE_FALSE" && (
              <Button
                variant="ghost"
                size="sm"
                className="mt-1"
                aria-label={`Remove option ${index + 1}`}
                disabled={fields.length <= 2}
                onClick={() => remove(index)}
              >
                <Trash2 aria-hidden className="h-4 w-4" />
              </Button>
            )}
          </div>
        ))}
        {(errors.options?.root?.message ?? errors.options?.message) ? (
          <p role="alert" className="text-sm text-destructive">
            {errors.options?.root?.message ?? errors.options?.message}
          </p>
        ) : null}
        {questionType !== "TRUE_FALSE" && (
          <Button
            variant="outline"
            size="sm"
            className="self-start"
            disabled={fields.length >= 20}
            onClick={() => append({ text: "", correct: false })}
          >
            <Plus aria-hidden className="h-4 w-4" />
            Add option
          </Button>
        )}
      </fieldset>

      <div className="flex flex-col gap-1.5">
        <label htmlFor="question-explanation" className="text-sm font-medium">
          Explanation <span className="font-normal text-muted-foreground">(optional)</span>
        </label>
        <textarea
          id="question-explanation"
          rows={2}
          className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          {...register("explanation")}
        />
        {errors.explanation && (
          <p role="alert" className="text-sm text-destructive">
            {errors.explanation.message}
          </p>
        )}
      </div>

      <FormField
        label="Tags (comma-separated, optional)"
        placeholder="exodus, moses"
        error={errors.tags?.message}
        {...register("tags")}
      />

      {submitError && (
        <p role="alert" className="text-sm text-destructive">
          {submitError}
        </p>
      )}

      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="ghost" onClick={confirmCancel}>
          Cancel
        </Button>
        <Button
          variant="secondary"
          isLoading={isSavingDraft}
          disabled={isPublishing}
          onClick={handleSubmit((values) => onSaveDraft(values))}
        >
          Save draft
        </Button>
        <Button type="submit" isLoading={isPublishing} disabled={isSavingDraft}>
          Publish
        </Button>
      </div>
    </form>
  );
}
