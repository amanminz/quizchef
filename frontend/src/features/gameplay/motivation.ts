import type { AnswerVerdict } from "@/features/gameplay/verdict";

/**
 * What a participant is told after each question — the whole of what they
 * are told about how they are doing, now that no rank appears on their
 * device until the ceremony has run.
 *
 * A small curated catalogue rather than anything generated at runtime: the
 * point of these lines is that a room full of people, some of them
 * children, some of them last, read something kind and true. That is a
 * thing to write once and check, not to leave to a model at 200ms notice
 * in front of a congregation.
 *
 * House rules for anything added here — every line must hold all four:
 *
 * 1. Never mention position, and never imply one ("catching up",
 *    "falling behind", "top of the class" are all out).
 * 2. Never compare the reader to anyone else.
 * 3. Never sarcastic, never disappointed, never consoling in a way that
 *    implies there is something to console.
 * 4. Look forward, not back — the next question is the subject.
 */
export type MotivationOutcome = AnswerVerdict | "final";

type Catalogue = Record<MotivationOutcome, string[]>;

/**
 * Every language offered to participants needs a full catalogue. The
 * default language is the fallback for anything else, so a language added
 * to the join form without one here degrades to English rather than
 * rendering an empty card.
 */
const CATALOGUES: Record<string, Catalogue> = {
  en: {
    correct: [
      "Great job—keep the momentum going!",
      "Nicely done—you knew that one.",
      "That's right—keep it up!",
      "Spot on—on to the next."
    ],
    incorrect: [
      "Keep going—the next question is a fresh chance.",
      "That one was tricky—the next is a clean slate.",
      "No worries—there's plenty of quiz left.",
      "Shake it off—the next one is waiting."
    ],
    unanswered: [
      "Stay ready—the next question is coming.",
      "No answer this time—the next one is yours.",
      "Get set—there's another question on the way."
    ],
    final: [
      "Well played! Please watch the shared screen for the results."
    ]
  },

  hi: {
    correct: [
      "बहुत बढ़िया! ऐसे ही आगे बढ़ते रहिए।",
      "शाबाश! आपने सही जवाब दिया।",
      "बिलकुल सही! बहुत अच्छा कर रहे हैं।",
      "एकदम सही! अब अगले सवाल के लिए तैयार हो जाइए।"
    ],

    incorrect: [
      "कोई बात नहीं! अगला सवाल एक नया मौका है।",
      "यह सवाल थोड़ा मुश्किल था। अगले सवाल में फिर कोशिश कीजिए।",
      "हिम्मत बनाए रखिए—अभी और सवाल बाकी हैं।",
      "कोई बात नहीं! अब अगले सवाल पर ध्यान दीजिए।"
    ],

    unanswered: [
      "तैयार रहिए—अगला सवाल आने वाला है।",
      "इस बार जवाब नहीं दे पाए—कोई बात नहीं, अगला सवाल आपका है।",
      "जल्दी तैयार हो जाइए—अगला सवाल आ रहा है।"
    ],

    final: [
      "बहुत बढ़िया! नतीजों के लिए सामने वाली स्क्रीन पर नज़र रखिए।"
    ]
  }
};

const DEFAULT_LANGUAGE = "en";

export interface MotivationKey {
  sessionId: string | undefined;
  participantId: string | undefined;
  /** 1-based, as the server numbers questions. */
  questionNumber: number | undefined;
  outcome: MotivationOutcome;
  /** The participant's chosen language; falls back when uncatalogued. */
  language: string | undefined;
}

/**
 * The line for one participant, on one question, with one outcome.
 *
 * Deterministic by construction — the same key always picks the same line,
 * so a refresh or a reconnect mid-reveal restores the message the player
 * was already reading rather than swapping it for another. Nothing is
 * stored to achieve that: the key is the state.
 *
 * The question number is *added* to the hash rather than mixed into it, so
 * consecutive questions with the same outcome always land on adjacent
 * entries — which is to say, never the same one twice in a row. A hash of
 * the whole key would be deterministic too, but would happily repeat a
 * line on questions 4 and 5, which reads like the app has stopped paying
 * attention.
 */
export function motivationFor({
  sessionId,
  participantId,
  questionNumber,
  outcome,
  language
}: MotivationKey): string {
  const catalogue = CATALOGUES[language ?? DEFAULT_LANGUAGE] ?? CATALOGUES[DEFAULT_LANGUAGE];
  const options = catalogue[outcome];
  if (options.length === 1) {
    return options[0];
  }
  const seed = hash(`${sessionId ?? ""}:${participantId ?? ""}:${outcome}`);
  return options[(seed + (questionNumber ?? 0)) % options.length];
}

/** Whether a language has its own catalogue (rather than falling back). */
export function hasMotivationCatalogue(language: string | undefined): boolean {
  return language !== undefined && language in CATALOGUES;
}

/** FNV-1a, 32-bit. Small, stable, and identical on every device. */
function hash(value: string): number {
  let result = 0x811c9dc5;
  for (let index = 0; index < value.length; index++) {
    result ^= value.charCodeAt(index);
    result = Math.imul(result, 0x01000193);
  }
  return Math.abs(result);
}
