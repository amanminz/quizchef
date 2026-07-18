import { z } from "zod";
import type {
  CreateQuestionRequest,
  QuestionResponse,
  QuestionType,
  UpdateQuestionRequest
} from "@/types/api";
import { languageCodeSchema, titleSchema } from "@/utils/validation";

/**
 * The question editor's form model and its mapping to the API's contracts.
 * Mirrors the backend's structural rules (RFC-003) for faster feedback —
 * the server remains the authority: a draft must already be a structurally
 * valid question (option rules of its type), only publishing freezes it.
 */

export const QUESTION_TYPES: { value: QuestionType; label: string }[] = [
  { value: "SINGLE_CHOICE", label: "Single choice" },
  { value: "MULTIPLE_CHOICE", label: "Multiple answers" },
  { value: "TRUE_FALSE", label: "True / false" }
];

const optionSchema = z.object({
  /** Present when editing — options keep their ids so translations survive. */
  id: z.string().optional(),
  text: z.string().trim().min(1, "Option text is required").max(500, "Option text is too long"),
  correct: z.boolean()
});

export const questionEditorSchema = z
  .object({
    questionType: z.enum(["SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE"]),
    difficulty: z.enum(["EASY", "MEDIUM", "HARD"]),
    defaultLanguage: languageCodeSchema,
    title: titleSchema,
    prompt: z.string().trim().min(1, "Prompt is required").max(2000, "Prompt is too long"),
    explanation: z.string().trim().max(2000, "Explanation is too long").optional(),
    /** Comma-separated in the form; parsed into the API's tag-name list. */
    tags: z.string().trim().optional(),
    options: z.array(optionSchema).min(2, "At least two options are required").max(20)
  })
  .superRefine((values, context) => {
    const correctCount = values.options.filter((option) => option.correct).length;
    switch (values.questionType) {
      case "SINGLE_CHOICE":
        if (correctCount !== 1) {
          context.addIssue({
            code: z.ZodIssueCode.custom,
            path: ["options"],
            message: "A single-choice question needs exactly one correct option"
          });
        }
        break;
      case "MULTIPLE_CHOICE":
        if (correctCount < 1) {
          context.addIssue({
            code: z.ZodIssueCode.custom,
            path: ["options"],
            message: "Mark at least one option as correct"
          });
        }
        break;
      case "TRUE_FALSE":
        if (values.options.length !== 2) {
          context.addIssue({
            code: z.ZodIssueCode.custom,
            path: ["options"],
            message: "A true/false question has exactly two options"
          });
        } else if (correctCount !== 1) {
          context.addIssue({
            code: z.ZodIssueCode.custom,
            path: ["options"],
            message: "Mark exactly one option as correct"
          });
        }
        break;
    }
    for (const name of parseTags(values.tags)) {
      if (name.length > 30) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["tags"],
          message: "Each tag must be at most 30 characters"
        });
        break;
      }
    }
  });

export type QuestionEditorValues = z.infer<typeof questionEditorSchema>;

export function emptyEditorValues(): QuestionEditorValues {
  return {
    questionType: "SINGLE_CHOICE",
    difficulty: "EASY",
    defaultLanguage: "en",
    title: "",
    prompt: "",
    explanation: "",
    tags: "",
    options: [
      { text: "", correct: true },
      { text: "", correct: false }
    ]
  };
}

/** The fixed option pair a TRUE_FALSE question starts from. */
export function trueFalseOptions(): QuestionEditorValues["options"] {
  return [
    { text: "True", correct: true },
    { text: "False", correct: false }
  ];
}

export function parseTags(tags: string | undefined): string[] {
  return (tags ?? "")
    .split(",")
    .map((name) => name.trim().toLowerCase())
    .filter((name) => name.length > 0)
    .slice(0, 20);
}

export function toCreateRequest(values: QuestionEditorValues): CreateQuestionRequest {
  return {
    defaultLanguage: values.defaultLanguage,
    questionType: values.questionType,
    difficulty: values.difficulty,
    localization: {
      title: values.title,
      prompt: values.prompt,
      explanation: values.explanation || undefined
    },
    options: values.options.map((option, index) => ({
      text: option.text,
      correct: option.correct,
      displayOrder: index + 1
    })),
    tags: parseTags(values.tags)
  };
}

/**
 * Builds the full-replacement PUT body for a draft. Existing options keep
 * their ids (translations survive), new options get fresh client ids as
 * the contract requires. Only the default language is edited here; other
 * localizations are carried over verbatim when their option texts still
 * cover every option, and dropped when the option change left them
 * incomplete — the same pruning the domain applies.
 */
export function toUpdateRequest(
  values: QuestionEditorValues,
  question: QuestionResponse
): UpdateQuestionRequest {
  const options = values.options.map((option, index) => ({
    id: option.id ?? crypto.randomUUID(),
    correct: option.correct,
    displayOrder: index + 1
  }));
  const optionIds = new Set(options.map((option) => option.id));

  const defaultLocalization = {
    languageCode: values.defaultLanguage,
    title: values.title,
    prompt: values.prompt,
    explanation: values.explanation || undefined,
    optionTexts: values.options.map((option, index) => ({
      optionId: options[index].id,
      text: option.text
    }))
  };

  const carriedLocalizations = (question.localizations ?? [])
    .filter((localization) => localization.languageCode !== values.defaultLanguage)
    .map((localization) => ({
      ...localization,
      optionTexts: localization.optionTexts.filter((text) => optionIds.has(text.optionId))
    }))
    .filter((localization) => localization.optionTexts.length === optionIds.size);

  return {
    version: question.version ?? 0,
    difficulty: values.difficulty,
    options,
    localizations: [defaultLocalization, ...carriedLocalizations],
    bibleReferences: question.bibleReferences ?? [],
    mediaReferences: question.mediaReferences ?? [],
    tags: parseTags(values.tags)
  };
}

/** Rehydrates the form from a loaded draft, in option display order. */
export function toFormValues(question: QuestionResponse): QuestionEditorValues {
  const defaultLanguage = question.defaultLanguage ?? "en";
  const localization = question.localizations?.find(
    (candidate) => candidate.languageCode === defaultLanguage
  );
  const textByOptionId = new Map(
    (localization?.optionTexts ?? []).map((text) => [text.optionId, text.text ?? ""])
  );
  const orderedOptions = [...(question.options ?? [])].sort(
    (a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)
  );

  return {
    questionType: question.questionType ?? "SINGLE_CHOICE",
    difficulty: question.difficulty ?? "EASY",
    defaultLanguage,
    title: localization?.title ?? "",
    prompt: localization?.prompt ?? "",
    explanation: localization?.explanation ?? "",
    tags: (question.tags ?? [])
      .map((tag) => tag.name)
      .filter((name): name is string => Boolean(name))
      .join(", "),
    options: orderedOptions.map((option) => ({
      id: option.id,
      text: textByOptionId.get(option.id ?? "") ?? "",
      correct: option.correct ?? false
    }))
  };
}
