import { Check, Copy, Share2 } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/common/Button";
import { Card, CardContent } from "@/components/common/Card";

export interface JoinCodeCardProps {
  sessionPin: string | undefined;
  /** Enables "Copy join message" — a ready-to-paste chat invitation. */
  quizTitle?: string;
  /** Larger code for the projected layout. */
  presentation?: boolean;
}

/**
 * The session PIN, displayed big enough to read off a projected screen,
 * with copy affordances for chat-based sharing: the bare code, and a
 * ready-to-paste join message carrying the deployed participant URL.
 */
export function JoinCodeCard({ sessionPin, quizTitle, presentation = false }: JoinCodeCardProps) {
  const [copied, setCopied] = useState<"code" | "message" | null>(null);

  const copyText = async (kind: "code" | "message", text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(kind);
      setTimeout(() => setCopied(null), 2_000);
    } catch {
      // Clipboard access denied — the code is on screen either way.
    }
  };

  const copy = async () => {
    if (sessionPin) {
      await copyText("code", sessionPin);
    }
  };

  const copyJoinMessage = async () => {
    if (!sessionPin) {
      return;
    }
    // The configured production frontend URL is this app's own origin.
    const joinUrl = `${window.location.origin}/play/${sessionPin}`;
    const message = [
      quizTitle ? `Join ${quizTitle}` : "Join the quiz",
      `Code: ${sessionPin}`,
      joinUrl
    ].join("\n\n");
    await copyText("message", message);
  };

  return (
    <Card>
      <CardContent className="flex flex-col items-center gap-3 p-6">
        <span className="text-sm font-medium uppercase tracking-wide text-muted-foreground">
          Session code
        </span>
        <span
          aria-label={`Session code ${sessionPin ?? "unavailable"}`}
          className={
            presentation
              ? "font-mono text-7xl font-bold tracking-[0.3em]"
              : "font-mono text-4xl font-bold tracking-[0.3em]"
          }
        >
          {sessionPin ?? "——————"}
        </span>
        <div className="flex flex-wrap justify-center gap-2">
          <Button variant="outline" size="sm" onClick={() => void copy()} disabled={!sessionPin}>
            {copied === "code" ? (
              <>
                <Check aria-hidden className="h-4 w-4" /> Copied
              </>
            ) : (
              <>
                <Copy aria-hidden className="h-4 w-4" /> Copy code
              </>
            )}
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => void copyJoinMessage()}
            disabled={!sessionPin}
          >
            {copied === "message" ? (
              <>
                <Check aria-hidden className="h-4 w-4" /> Copied
              </>
            ) : (
              <>
                <Share2 aria-hidden className="h-4 w-4" /> Copy Join Details
              </>
            )}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
