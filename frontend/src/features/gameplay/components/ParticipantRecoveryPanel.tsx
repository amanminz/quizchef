import { LifeBuoy } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/common/Button";
import { Card, CardContent } from "@/components/common/Card";
import { RecoveryCodeForm } from "@/features/gameplay/components/RecoveryCodeForm";

export interface ParticipantRecoveryPanelProps {
  /** The name this device believes it was playing under, if it knows one. */
  displayName?: string;
  onRetry: () => void;
  isRetrying: boolean;
  onRedeemCode: (code: string) => Promise<unknown>;
  isRedeeming: boolean;
  redeemError: unknown;
  /** Gives up on the old participation and starts fresh, on purpose. */
  onStartOver: () => void;
}

/**
 * What a player sees when the server will not accept their stored
 * credential.
 *
 * This screen exists because of what used to happen instead. A refused
 * credential dropped the player onto the join form, where typing their own
 * name — the only name they would think to type — was refused as already
 * taken, by a rule meant to protect them. The dead end looked like the
 * quiz rejecting them personally.
 *
 * So the refusal is now an offer. Retry first, because the commonest cause
 * is a session that was not reachable a moment ago. Then the host's code,
 * which is the only way to prove identity once browser storage is gone.
 * Starting over is last and explicit — it is the option that forfeits a
 * score, so it is never the one that happens by default.
 */
export function ParticipantRecoveryPanel({
  displayName,
  onRetry,
  isRetrying,
  onRedeemCode,
  isRedeeming,
  redeemError,
  onStartOver
}: ParticipantRecoveryPanelProps) {
  const [enteringCode, setEnteringCode] = useState(false);

  return (
    <Card>
      <CardContent className="space-y-4 pt-6">
        <div className="flex gap-3">
          <LifeBuoy aria-hidden className="mt-0.5 h-5 w-5 shrink-0 text-muted-foreground" />
          <div className="space-y-1">
            <p className="font-medium">
              {displayName
                ? `We couldn't restore ${displayName}'s game`
                : "We couldn't restore your game"}
            </p>
            <p className="text-sm text-muted-foreground">
              Your progress is safe on the server — this device just can&rsquo;t prove it&rsquo;s
              yours right now.
            </p>
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          <Button onClick={onRetry} isLoading={isRetrying}>
            Try again
          </Button>
          {!enteringCode && (
            <Button variant="outline" onClick={() => setEnteringCode(true)}>
              Enter recovery code
            </Button>
          )}
        </div>

        {enteringCode ? (
          <div className="space-y-2 rounded-md border p-3">
            <p className="text-sm text-muted-foreground">
              Ask the Quiz Master for a recovery code and type it here. Your score and answers
              come back with you.
            </p>
            <RecoveryCodeForm
              onSubmit={onRedeemCode}
              isSubmitting={isRedeeming}
              error={redeemError}
            />
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">
            Having trouble? Ask the Quiz Master to recover your player.
          </p>
        )}

        <div className="border-t pt-3">
          {/* Last, quiet, and explicit: this is the one choice that gives
              up a score, so it must never be the path of least resistance. */}
          <Button variant="ghost" size="sm" onClick={onStartOver}>
            Start over as a new player
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
