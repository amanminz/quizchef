import { Check, Shuffle } from "lucide-react";
import { Button } from "@/components/common/Button";
import { Card, CardContent } from "@/components/common/Card";
import { ErrorPanel } from "@/components/common/ErrorPanel";

export interface ShuffleQuestionsCardProps {
  /** The server's answer to whether this session has its own order. */
  shuffled: boolean;
  onShuffle: () => void;
  isShuffling: boolean;
  error?: unknown;
}

/**
 * Lets the host draw a fresh question order for this session.
 *
 * Sits in the lobby because that is the last moment it is allowed: once a
 * question has opened, reordering would deal the room a question it has
 * already answered, and the server refuses it.
 *
 * Deliberately does not show the drawn order. The server draws it, and a
 * host who could see the result would be choosing rather than shuffling —
 * reshuffling until the "right" first question came up is exactly the thing
 * a shuffle is for avoiding. All the host needs to know is that this
 * session will differ from the last one.
 */
export function ShuffleQuestionsCard({
  shuffled,
  onShuffle,
  isShuffling,
  error
}: ShuffleQuestionsCardProps) {
  return (
    <Card>
      <CardContent className="flex flex-col gap-3 p-4">
        <div className="flex items-start justify-between gap-3">
          <div className="flex flex-col gap-0.5">
            <span className="text-sm font-semibold">Question order</span>
            <span className="text-xs text-muted-foreground">
              {shuffled
                ? "Shuffled for this session"
                : "The quiz's own order"}
            </span>
          </div>
          {shuffled && (
            <span className="flex shrink-0 items-center gap-1 rounded-full bg-success/15 px-2 py-0.5 text-xs font-bold text-success">
              <Check aria-hidden className="h-3.5 w-3.5" />
              Shuffled
            </span>
          )}
        </div>

        <Button variant="outline" size="sm" onClick={onShuffle} isLoading={isShuffling}>
          <Shuffle aria-hidden className="h-4 w-4" />
          {shuffled ? "Shuffle again" : "Shuffle questions"}
        </Button>

        <p className="text-xs text-muted-foreground">
          Changes the order for this session only — the quiz itself is not edited, so a
          group playing it again gets a different run.
        </p>

        {error != null && <ErrorPanel title="Could not shuffle the questions" error={error} />}
      </CardContent>
    </Card>
  );
}
