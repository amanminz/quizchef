import { isApiClientError } from "@/api/apiError";

/**
 * Why a resume failed, and — the part that matters — whether the stored
 * credential should survive it.
 *
 * A live event produces far more temporary failures than real ones: a phone
 * waking on a weak signal, a venue's shared address hitting a rate limit
 * when the whole room reconnects at once, a backend restarting between
 * questions. Treating any of those the way an invalid credential is treated
 * throws away a perfectly good participation and sends the player to the
 * join form, where the display-name rule then refuses them their own name.
 * That is the production failure this classification exists to make
 * impossible.
 *
 * The default is deliberately "temporary". A failure nobody anticipated is
 * far more likely to be a network or server problem than proof that the
 * player is not who they say — and the cost of being wrong is asymmetric:
 * keeping a dead credential wastes a retry, while discarding a live one
 * loses somebody's game.
 */
export type ResumeFailure =
  /** The server does not recognize this credential. It will never work. */
  | { kind: "credential-rejected" }
  /** Nothing is wrong with the credential; the request did not land. */
  | { kind: "temporary"; retryAfterSeconds: number | null };

export function classifyResumeFailure(error: unknown): ResumeFailure {
  if (!isApiClientError(error)) {
    return { kind: "temporary", retryAfterSeconds: null };
  }
  // The one definitive answer: this session, right now, has no participant
  // holding that credential. Every other status is about the request, not
  // about the player.
  if (error.code === "session.participant.not-found") {
    return { kind: "credential-rejected" };
  }
  return { kind: "temporary", retryAfterSeconds: error.retryAfterSeconds };
}

/**
 * How long to wait before trying again — the server's own `Retry-After`
 * when it gave one, otherwise an exponential back-off from two seconds,
 * capped so a player who leaves the page open is never more than half a
 * minute from being back in the game.
 */
export function resumeRetryDelayMs(attempt: number, retryAfterSeconds: number | null): number {
  if (retryAfterSeconds != null) {
    return Math.min(retryAfterSeconds * 1000, 60_000);
  }
  return Math.min(2_000 * 2 ** Math.max(0, attempt - 1), 30_000);
}
