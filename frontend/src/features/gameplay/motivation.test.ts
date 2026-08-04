import { describe, expect, it } from "vitest";
import { hasMotivationCatalogue, motivationFor } from "@/features/gameplay/motivation";

const BASE = {
  sessionId: "session-1",
  participantId: "participant-me",
  questionNumber: 1,
  language: "en"
} as const;

describe("motivationFor", () => {
  it("matches the message to the outcome", () => {
    const correct = motivationFor({ ...BASE, outcome: "correct" });
    const incorrect = motivationFor({ ...BASE, outcome: "incorrect" });
    const unanswered = motivationFor({ ...BASE, outcome: "unanswered" });

    expect(correct).not.toBe(incorrect);
    expect(incorrect).not.toBe(unanswered);
    // The wrong-answer line looks forward rather than back.
    expect(incorrect.toLowerCase()).toMatch(/next|fresh|plenty|waiting/);
  });

  it("renders the player's own language, and falls back rather than blanking", () => {
    const hindi = motivationFor({ ...BASE, outcome: "correct", language: "hi" });
    expect(hindi).toMatch(/[ऀ-ॿ]/);

    // A language with no catalogue of its own still gets a real message.
    const uncatalogued = motivationFor({ ...BASE, outcome: "correct", language: "ta" });
    expect(uncatalogued).toBe(motivationFor({ ...BASE, outcome: "correct", language: "en" }));
    expect(hasMotivationCatalogue("ta")).toBe(false);
    expect(hasMotivationCatalogue("hi")).toBe(true);
  });

  it("gives the same player the same line for the same question, every time", () => {
    // Determinism is what makes a refresh or a reconnect mid-reveal show
    // the message the player was already reading.
    const first = motivationFor({ ...BASE, outcome: "correct", questionNumber: 4 });
    const again = motivationFor({ ...BASE, outcome: "correct", questionNumber: 4 });
    expect(again).toBe(first);
  });

  it("never repeats a line on consecutive questions", () => {
    for (const outcome of ["correct", "incorrect", "unanswered"] as const) {
      for (let questionNumber = 1; questionNumber <= 12; questionNumber++) {
        expect(motivationFor({ ...BASE, outcome, questionNumber })).not.toBe(
          motivationFor({ ...BASE, outcome, questionNumber: questionNumber + 1 })
        );
      }
    }
  });

  it("varies between players, so a room does not read in unison", () => {
    const mine = motivationFor({ ...BASE, outcome: "correct" });
    const theirs = Array.from({ length: 8 }, (_, index) =>
      motivationFor({ ...BASE, participantId: `participant-${index}`, outcome: "correct" })
    );
    expect(theirs.some((message) => message !== mine)).toBe(true);
  });

  it("says nothing about position, in any language or outcome", () => {
    // The one rule the whole catalogue exists to keep: these lines are the
    // only feedback a participant gets, and none of them may hint at where
    // they stand.
    // Hindi note: bare आगे is *not* on this list. "आगे बढ़ते रहें" is the
    // ordinary idiom for "keep going", the same way English "move on" is
    // not a claim about position; the positional words are the nouns
    // (स्थान, रैंक) and the explicit comparatives (आगे हैं, पीछे हैं).
    const forbidden =
      /rank|place[sd]?\b|position|first|last|top|bottom|ahead|behind|winning|losing|leader|catch up|स्थान|रैंक|प्रथम|अंतिम|आगे हैं|पीछे हैं/i;
    for (const language of ["en", "hi"]) {
      for (const outcome of ["correct", "incorrect", "unanswered", "final"] as const) {
        for (let questionNumber = 1; questionNumber <= 12; questionNumber++) {
          expect(
            motivationFor({ ...BASE, outcome, language, questionNumber })
          ).not.toMatch(forbidden);
        }
      }
    }
  });

  it("closes the quiz by pointing at the shared screen, not at a result", () => {
    expect(motivationFor({ ...BASE, outcome: "final" })).toMatch(/shared screen/i);
    expect(motivationFor({ ...BASE, outcome: "final", language: "hi" })).toMatch(/स्क्रीन/);
  });

  it("still produces a message before the ids are known", () => {
    // A first render can precede the reconnect that supplies them; an
    // empty card would be worse than a slightly less varied line.
    expect(
      motivationFor({
        sessionId: undefined,
        participantId: undefined,
        questionNumber: undefined,
        outcome: "correct",
        language: undefined
      })
    ).toBeTruthy();
  });
});
