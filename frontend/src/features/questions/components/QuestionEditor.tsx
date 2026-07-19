import { Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { useFieldArray, useForm } from "react-hook-form";
import { Button } from "@/components/common/Button";
import { FormField } from "@/components/forms/FormField";
import { EVENT_LANGUAGES, languageLabel } from "@/features/gameplay/eventLanguages";
import {
  emptyEditorValues,
  QUESTION_TYPES,
  questionEditorSchema,
  translationIncluded,
  translationLanguageFor,
  trueFalseOptions,
  type QuestionEditorValues
} from "@/features/questions/editorForm";
import type { QuestionType } from "@/types/api";
import { cn } from "@/utils/cn";
import { zodForm } from "@/utils/validation";

const DIFFICULTIES = ["EASY", "MEDIUM", "HARD"] as const;

export interface QuestionEditorProps {
  initialValues?: QuestionEditorValues;
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
 *
 * One logical question, two language tabs: structure (type, difficulty,
 * correctness, option order, title, tags) is shared and always visible;
 * prompt, option texts, and explanation live behind the language tabs.
 * Drafts may change type and default language; switching to True/False
 * replaces the options only after explicit confirmation, since it
 * discards option identities and their translations.
 */
export function QuestionEditor({
  initialValues,
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
    getValues,
    control,
    formState: { errors, isDirty }
  } = useForm<QuestionEditorValues>(
    zodForm(questionEditorSchema, { defaultValues: initialValues ?? emptyEditorValues() })
  );
  const { fields, append, remove, replace } = useFieldArray({ control, name: "options" });
  const [activeTab, setActiveTab] = useState<"default" | "translation">("default");

  const questionType = watch("questionType");
  const defaultLanguage = watch("defaultLanguage");
  const options = watch("options");
  const translatedPrompt = watch("translatedPrompt");
  const translatedExplanation = watch("translatedExplanation");
  const exclusiveCorrect = questionType !== "MULTIPLE_CHOICE";
  const translationLanguage = translationLanguageFor(defaultLanguage);
  const hasTranslation = translationIncluded({
    translatedPrompt,
    translatedExplanation,
    options: options ?? []
  });
  const onTranslationTab = activeTab === "translation";

  function handleTypeChange(nextType: QuestionType) {
    if (nextType === questionType) {
      return;
    }
    if (nextType === "TRUE_FALSE") {
      const confirmed = window.confirm(
        "Switching to True/False replaces the current options with True and False. " +
          "Translated option texts will need to be re-entered. Continue?"
      );
      if (!confirmed) {
        return;
      }
      replace(trueFalseOptions());
    } else if (nextType === "SINGLE_CHOICE") {
      // Keep the options but collapse to a single correct one.
      const current = getValues("options");
      const firstCorrect = current.findIndex((option) => option.correct);
      current.forEach((_, index) =>
        setValue(`options.${index}.correct`, index === Math.max(firstCorrect, 0))
      );
    }
    // TRUE_FALSE → choice types keeps the two options as a valid editable start.
    setValue("questionType", nextType, { shouldDirty: true });
  }

  /**
   * Changing the default language flips which language is "the default"
   * and which is "the translation". When the new default is exactly the
   * current translation language (the en ↔ hi flip), the authored texts
   * swap tabs so nothing is lost or mislabeled.
   */
  function handleLanguageChange(nextLanguage: string) {
    if (nextLanguage === defaultLanguage) {
      return;
    }
    if (nextLanguage === translationLanguage) {
      const values = getValues();
      setValue("prompt", values.translatedPrompt ?? "", { shouldDirty: true });
      setValue("translatedPrompt", values.prompt);
      setValue("explanation", values.translatedExplanation ?? "");
      setValue("translatedExplanation", values.explanation ?? "");
      values.options.forEach((option, index) => {
        setValue(`options.${index}.text`, option.translatedText ?? "");
        setValue(`options.${index}.translatedText`, option.text);
      });
    }
    setValue("defaultLanguage", nextLanguage, { shouldDirty: true });
  }

  function confirmCancel() {
    if (!isDirty || window.confirm("Discard unsaved changes?")) {
      onCancel();
    }
  }

  const languageOptions = EVENT_LANGUAGES.some((language) => language.value === defaultLanguage)
    ? EVENT_LANGUAGES
    : [...EVENT_LANGUAGES, { value: defaultLanguage, label: languageLabel(defaultLanguage) }];

  return (
    <form
      onSubmit={handleSubmit((values) => onPublish(values))}
      noValidate
      className="flex flex-col gap-4"
    >
      <FormField label="Title" error={errors.title?.message} {...register("title")} />

      <div className="grid gap-4 sm:grid-cols-3">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="question-type" className="text-sm font-medium">
            Question type
          </label>
          <select
            id="question-type"
            value={questionType}
            onChange={(event) => handleTypeChange(event.target.value as QuestionType)}
            className="h-10 rounded-md border border-input bg-background px-3 text-sm"
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
            Default language
          </label>
          <select
            id="question-language"
            value={defaultLanguage}
            onChange={(event) => handleLanguageChange(event.target.value)}
            className="h-10 rounded-md border border-input bg-background px-3 text-sm"
          >
            {languageOptions.map((language) => (
              <option key={language.value} value={language.value}>
                {language.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div role="tablist" aria-label="Content language" className="flex gap-1 border-b border-border">
        <LanguageTab
          label={languageLabel(defaultLanguage)}
          active={!onTranslationTab}
          onSelect={() => setActiveTab("default")}
        />
        <LanguageTab
          label={`${languageLabel(translationLanguage)}${hasTranslation ? "" : " (optional)"}`}
          active={onTranslationTab}
          onSelect={() => setActiveTab("translation")}
        />
      </div>

      {onTranslationTab && (
        <p className="text-sm text-muted-foreground">
          Translations share the question's options and correct answer. Fill the prompt and every
          option text — or clear all fields to publish without a{" "}
          {languageLabel(translationLanguage)} version.
        </p>
      )}

      <div className={onTranslationTab ? "hidden" : "contents"}>
        <PromptField
          id="question-prompt"
          label="Prompt"
          error={errors.prompt?.message}
          registration={register("prompt")}
        />
      </div>
      <div className={onTranslationTab ? "contents" : "hidden"}>
        <PromptField
          id="question-prompt-translated"
          label={`Prompt (${languageLabel(translationLanguage)})`}
          error={errors.translatedPrompt?.message}
          registration={register("translatedPrompt")}
        />
      </div>

      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1 text-sm font-medium">
          Options
          <span className="ml-2 font-normal text-muted-foreground">
            {onTranslationTab
              ? `Translated texts — correctness is shared`
              : exclusiveCorrect
                ? "Mark the correct answer"
                : "Mark every correct answer"}
          </span>
        </legend>
        {fields.map((field, index) => (
          <div key={field.id} className="flex items-start gap-2">
            <input
              type={exclusiveCorrect ? "radio" : "checkbox"}
              name={exclusiveCorrect ? "correct-option" : undefined}
              checked={options[index]?.correct ?? false}
              disabled={onTranslationTab}
              onChange={(event) => markCorrect(index, event.target.checked)}
              aria-label={`Option ${index + 1} is correct`}
              className="mt-3 h-4 w-4 accent-primary disabled:opacity-60"
            />
            <div className="flex-1">
              <input
                aria-label={`Option ${index + 1} text`}
                aria-invalid={errors.options?.[index]?.text ? true : undefined}
                placeholder={`Option ${index + 1}`}
                className={cn(
                  "h-10 w-full rounded-md border border-input bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                  onTranslationTab && "hidden"
                )}
                {...register(`options.${index}.text`)}
              />
              <input
                aria-label={`Option ${index + 1} text (${languageLabel(translationLanguage)})`}
                aria-invalid={errors.options?.[index]?.translatedText ? true : undefined}
                placeholder={options[index]?.text || `Option ${index + 1}`}
                className={cn(
                  "h-10 w-full rounded-md border border-input bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                  !onTranslationTab && "hidden"
                )}
                {...register(`options.${index}.translatedText`)}
              />
              {!onTranslationTab && errors.options?.[index]?.text && (
                <p role="alert" className="mt-1 text-sm text-destructive">
                  {errors.options[index]?.text?.message}
                </p>
              )}
              {onTranslationTab && errors.options?.[index]?.translatedText && (
                <p role="alert" className="mt-1 text-sm text-destructive">
                  {errors.options[index]?.translatedText?.message}
                </p>
              )}
            </div>
            {questionType !== "TRUE_FALSE" && !onTranslationTab && (
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
        {questionType !== "TRUE_FALSE" && !onTranslationTab && (
          <Button
            variant="outline"
            size="sm"
            className="self-start"
            disabled={fields.length >= 20}
            onClick={() => append({ text: "", translatedText: "", correct: false })}
          >
            <Plus aria-hidden className="h-4 w-4" />
            Add option
          </Button>
        )}
      </fieldset>

      <div className={onTranslationTab ? "hidden" : "contents"}>
        <ExplanationField
          id="question-explanation"
          label="Explanation"
          error={errors.explanation?.message}
          registration={register("explanation")}
        />
      </div>
      <div className={onTranslationTab ? "contents" : "hidden"}>
        <ExplanationField
          id="question-explanation-translated"
          label={`Explanation (${languageLabel(translationLanguage)})`}
          error={errors.translatedExplanation?.message}
          registration={register("translatedExplanation")}
        />
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

  function markCorrect(index: number, correct: boolean) {
    if (exclusiveCorrect) {
      options.forEach((_, other) => setValue(`options.${other}.correct`, other === index));
    } else {
      setValue(`options.${index}.correct`, correct);
    }
  }
}

function LanguageTab({
  label,
  active,
  onSelect
}: {
  label: string;
  active: boolean;
  onSelect: () => void;
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onSelect}
      className={cn(
        "-mb-px rounded-t-md border-b-2 px-4 py-2 text-sm font-medium transition-colors",
        active
          ? "border-primary text-foreground"
          : "border-transparent text-muted-foreground hover:text-foreground"
      )}
    >
      {label}
    </button>
  );
}

function PromptField({
  id,
  label,
  error,
  registration
}: {
  id: string;
  label: string;
  error?: string;
  registration: ReturnType<ReturnType<typeof useForm<QuestionEditorValues>>["register"]>;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <textarea
        id={id}
        rows={3}
        aria-invalid={error ? true : undefined}
        className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        {...registration}
      />
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}

function ExplanationField({
  id,
  label,
  error,
  registration
}: {
  id: string;
  label: string;
  error?: string;
  registration: ReturnType<ReturnType<typeof useForm<QuestionEditorValues>>["register"]>;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label} <span className="font-normal text-muted-foreground">(optional)</span>
      </label>
      <textarea
        id={id}
        rows={2}
        className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        {...registration}
      />
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
