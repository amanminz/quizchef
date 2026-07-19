import { describe, expect, it } from "vitest";
import {
  emptyEditorValues,
  questionEditorSchema,
  toFormValues,
  toUpdateRequest,
  translationIncluded,
  translationLanguageFor,
  type QuestionEditorValues
} from "@/features/questions/editorForm";
import type { QuestionResponse } from "@/types/api";

function bilingualValues(): QuestionEditorValues {
  return {
    ...emptyEditorValues(),
    title: "Exodus leader",
    prompt: "Who led Israel out of Egypt?",
    explanation: "See Exodus 3.",
    translatedPrompt: "इस्राएल को मिस्र से कौन निकाल लाया?",
    translatedExplanation: "निर्गमन 3 देखें।",
    options: [
      { id: "opt-1", text: "Moses", translatedText: "मूसा", correct: true },
      { id: "opt-2", text: "Aaron", translatedText: "हारून", correct: false }
    ]
  };
}

function draftQuestion(): QuestionResponse {
  return {
    id: "q-1",
    version: 0,
    state: "DRAFT",
    questionType: "SINGLE_CHOICE",
    difficulty: "EASY",
    defaultLanguage: "en",
    options: [
      { id: "opt-1", correct: true, displayOrder: 1 },
      { id: "opt-2", correct: false, displayOrder: 2 }
    ],
    localizations: [
      {
        languageCode: "en",
        title: "Exodus leader",
        prompt: "Who led Israel out of Egypt?",
        optionTexts: [
          { optionId: "opt-1", text: "Moses" },
          { optionId: "opt-2", text: "Aaron" }
        ]
      }
    ],
    bibleReferences: [],
    mediaReferences: [],
    tags: []
  } as QuestionResponse;
}

describe("editorForm translations", () => {
  it("keeps one question: shared option ids and correct answer across both languages", () => {
    const request = toUpdateRequest(bilingualValues(), draftQuestion());

    const english = request.localizations.find((entry) => entry.languageCode === "en");
    const hindi = request.localizations.find((entry) => entry.languageCode === "hi");
    expect(english).toBeDefined();
    expect(hindi).toBeDefined();
    // Option identities are shared — the correct answer maps to ids, not text.
    expect(hindi!.optionTexts.map((text) => text.optionId)).toEqual(
      english!.optionTexts.map((text) => text.optionId)
    );
    expect(request.options).toEqual([
      { id: "opt-1", correct: true, displayOrder: 1 },
      { id: "opt-2", correct: false, displayOrder: 2 }
    ]);
    // The title is structural and shared.
    expect(hindi!.title).toBe(english!.title);
    expect(hindi!.prompt).toBe("इस्राएल को मिस्र से कौन निकाल लाया?");
  });

  it("omits the translation entirely when its fields are empty", () => {
    const values = { ...bilingualValues(), translatedPrompt: "", translatedExplanation: "" };
    values.options = values.options.map((option) => ({ ...option, translatedText: "" }));

    const request = toUpdateRequest(values, draftQuestion());

    expect(request.localizations.map((entry) => entry.languageCode)).toEqual(["en"]);
  });

  it("blocks an incomplete translation from validating", () => {
    const values = bilingualValues();
    values.options[1].translatedText = "";

    const result = questionEditorSchema.safeParse(values);

    expect(result.success).toBe(false);
    expect(translationIncluded(values)).toBe(true);
  });

  it("a single-language question stays valid and editable", () => {
    const values = {
      ...bilingualValues(),
      translatedPrompt: "",
      translatedExplanation: "",
      options: bilingualValues().options.map((option) => ({ ...option, translatedText: "" }))
    };

    expect(questionEditorSchema.safeParse(values).success).toBe(true);
  });

  it("rehydrates both languages from a stored question", () => {
    const question = draftQuestion();
    question.localizations!.push({
      languageCode: "hi",
      title: "Exodus leader",
      prompt: "इस्राएल को मिस्र से कौन निकाल लाया?",
      optionTexts: [
        { optionId: "opt-1", text: "मूसा" },
        { optionId: "opt-2", text: "हारून" }
      ]
    });

    const values = toFormValues(question);

    expect(values.translatedPrompt).toBe("इस्राएल को मिस्र से कौन निकाल लाया?");
    expect(values.options.map((option) => option.translatedText)).toEqual(["मूसा", "हारून"]);
  });

  it("sends the draft's question type and default language on every update", () => {
    const request = toUpdateRequest(
      { ...bilingualValues(), questionType: "MULTIPLE_CHOICE" },
      draftQuestion()
    );

    expect(request.questionType).toBe("MULTIPLE_CHOICE");
    expect(request.defaultLanguage).toBe("en");
  });

  it("pairs Hindi with English defaults and English with Hindi defaults", () => {
    expect(translationLanguageFor("en")).toBe("hi");
    expect(translationLanguageFor("hi")).toBe("en");
    expect(translationLanguageFor("kn")).toBe("hi");
  });
});
