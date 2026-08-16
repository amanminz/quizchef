import { useMutation } from "@tanstack/react-query";
import { sessionApi } from "@/api/sessionApi";

/**
 * Mints a recovery code for one participant, for the host to read out.
 *
 * Deliberately no cache: the response is a live credential for the next few
 * minutes, and a query cache is a place it would sit around being re-read
 * long after it stopped being useful. Each click asks for a fresh one, and
 * the server supersedes whatever it replaced.
 */
export function useIssueRecoveryCode(sessionId: string | undefined) {
  return useMutation({
    mutationFn: (participantId: string) =>
      sessionApi.issueRecoveryCode(sessionId!, participantId)
  });
}
