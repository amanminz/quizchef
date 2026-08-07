import { isApiClientError } from "@/api/apiError";

/**
 * The conflicts that mean "the game already moved" rather than "something
 * went wrong".
 *
 * A host command can lose a race it was never really in. The question timer
 * closes questions on the server's own schedule, so a host tapping *Close
 * Question* as the clock runs out arrives second; a double-tapped button, or
 * a second host tab, does the same. Two shapes come back:
 *
 * - `session.invalid-transition` — the phase already changed, so the command
 *   no longer applies.
 * - `conflict.concurrent-modification` — the aggregate's version moved under
 *   the request, because two writers touched the session together.
 *
 * Both describe a session that is *ahead* of this client, not a broken one.
 * The right response is to re-read the server's state and render it, which
 * is what the host wanted anyway — the transition happened. Showing "could
 * not advance the game" over a game that advanced fine is the actual bug.
 *
 * Deliberately narrow. A 404, a 403, a 500, or a validation failure are all
 * real and still surface as errors; nothing here retries a command, because
 * a command that lost a race must not be reissued against whatever state
 * replaced it.
 */
const CONVERGENT_CONFLICT_CODES = new Set([
  "session.invalid-transition",
  "conflict.concurrent-modification"
]);

export function isConvergentConflict(error: unknown): boolean {
  return isApiClientError(error) && CONVERGENT_CONFLICT_CODES.has(error.code ?? "");
}
