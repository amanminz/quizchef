import { useQuery } from "@tanstack/react-query";
import { sessionApi } from "@/api/sessionApi";
import { gameplayKeys } from "@/features/gameplay/queryKeys";

/**
 * A finished session's captured standings — host only, and the complete
 * field.
 *
 * Historical rather than live: the rows were written when the session ended
 * and are read back unchanged, so this never needs the phase gating the
 * in-play reads carry. It is enabled only once the session has actually
 * finished, because before that there is nothing recorded and the answer
 * would be an empty list that looks like a bug.
 */
export function useFinalStandings(sessionId: string | undefined, finished: boolean) {
  return useQuery({
    queryKey: gameplayKeys.finalStandings(sessionId ?? ""),
    queryFn: () => sessionApi.finalStandings(sessionId!),
    enabled: sessionId !== undefined && finished,
    // History does not change. Re-reading it on every focus would be noise.
    staleTime: Infinity
  });
}
