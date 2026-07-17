import { Check, Copy } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/common/Button";
import { Card, CardContent } from "@/components/common/Card";

/**
 * The session PIN, displayed big enough to read off a projected screen,
 * with a copy affordance for chat-based sharing.
 */
export function JoinCodeCard({ sessionPin }: { sessionPin: string | undefined }) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    if (!sessionPin) {
      return;
    }
    try {
      await navigator.clipboard.writeText(sessionPin);
      setCopied(true);
      setTimeout(() => setCopied(false), 2_000);
    } catch {
      // Clipboard access denied — the code is on screen either way.
    }
  };

  return (
    <Card>
      <CardContent className="flex flex-col items-center gap-3 p-6">
        <span className="text-sm font-medium uppercase tracking-wide text-muted-foreground">
          Session code
        </span>
        <span
          aria-label={`Session code ${sessionPin ?? "unavailable"}`}
          className="font-mono text-4xl font-bold tracking-[0.3em]"
        >
          {sessionPin ?? "——————"}
        </span>
        <Button variant="outline" size="sm" onClick={() => void copy()} disabled={!sessionPin}>
          {copied ? (
            <>
              <Check aria-hidden className="h-4 w-4" /> Copied
            </>
          ) : (
            <>
              <Copy aria-hidden className="h-4 w-4" /> Copy code
            </>
          )}
        </Button>
      </CardContent>
    </Card>
  );
}
