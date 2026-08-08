import { Tv } from "lucide-react";

export interface FinalResultsPendingScreenProps {
  /** The participant's chosen language; falls back when uncatalogued. */
  language?: string;
}

/**
 * The wait between the last question ending and the host releasing final
 * standings — backend-enforced (the placement read refuses until release),
 * not merely hidden with CSS. A refresh or reconnect during this window
 * re-derives the same phase from the session summary, so it never flashes a
 * rank that has not been released.
 *
 * <p>Localized because this is the screen a participant sits on longest —
 * through the whole podium ceremony — and a player who chose Hindi to play
 * in should not be handed English at the one moment they are waiting and
 * wondering whether something has gone wrong.
 */
const COPY: Record<string, { heading: string; body: string[] }> = {
  en: {
    heading: "Quiz complete!",
    body: ["The winners are being announced.", "Please watch the main screen."]
  },
  hi: {
    heading: "क्विज़ पूरा हुआ!",
    body: ["विजेताओं की घोषणा हो रही है।", "कृपया सामने वाली स्क्रीन देखें।"]
  }
};

const DEFAULT_LANGUAGE = "en";

export function FinalResultsPendingScreen({ language }: FinalResultsPendingScreenProps = {}) {
  const copy = COPY[language ?? DEFAULT_LANGUAGE] ?? COPY[DEFAULT_LANGUAGE];

  return (
    <div
      role="status"
      className="flex flex-col items-center gap-3 rounded-lg border border-dashed px-6 py-16 text-center"
    >
      <Tv aria-hidden className="h-8 w-8 text-muted-foreground" />
      <p className="text-xl font-bold">{copy.heading}</p>
      <p className="max-w-sm text-sm text-muted-foreground">
        {copy.body.map((line, index) => (
          <span key={line}>
            {line}
            {index < copy.body.length - 1 && <br />}
          </span>
        ))}
      </p>
    </div>
  );
}
