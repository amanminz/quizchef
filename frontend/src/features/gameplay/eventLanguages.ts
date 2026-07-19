/**
 * The one configurable allowlist of languages offered to joining
 * participants (and to authors as translation targets). Trimmed to the
 * languages of the current live event; to re-enable a language later,
 * move it back from `DORMANT_LANGUAGES` — no component changes needed.
 */
export interface EventLanguage {
  value: string;
  /** Shown in the language's own script so players recognize it instantly. */
  label: string;
}

export const EVENT_LANGUAGES: EventLanguage[] = [
  { value: "en", label: "English" },
  { value: "hi", label: "हिन्दी" }
];

/** Previously offered languages, parked until an event needs them again. */
export const DORMANT_LANGUAGES: EventLanguage[] = [
  { value: "kn", label: "ಕನ್ನಡ" },
  { value: "ta", label: "தமிழ்" },
  { value: "te", label: "తెలుగు" },
  { value: "ml", label: "മലയാളം" }
];

export function languageLabel(value: string): string {
  return (
    [...EVENT_LANGUAGES, ...DORMANT_LANGUAGES].find((language) => language.value === value)
      ?.label ?? value
  );
}
