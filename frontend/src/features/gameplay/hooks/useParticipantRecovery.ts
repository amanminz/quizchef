import { useMutation } from "@tanstack/react-query";
import { sessionApi } from "@/api/sessionApi";
import { usePlayerSessionStore } from "@/features/gameplay/playerSessionStore";

/**
 * Redeems the code the host read out, and stores the fresh credential it
 * comes back with.
 *
 * Storing it is the whole point of the flow: recovery exists because this
 * device has no credential, so a recovery that did not leave one behind
 * would strand the player again on their next refresh. The new token also
 * replaces the old one server-side, so the device that lost the game — a
 * phone left at home, or someone else's — stops being able to resume.
 */
export function useParticipantRecovery(pin: string) {
  const record = usePlayerSessionStore((state) => state.record);

  return useMutation({
    mutationFn: (recoveryCode: string) => sessionApi.recoverParticipant(pin, { recoveryCode }),
    onSuccess: (recovered) => {
      const session = recovered.session;
      if (!session?.sessionId || !session.participantId) {
        return;
      }
      record(pin, {
        sessionId: session.sessionId,
        participantId: session.participantId,
        resumeToken: recovered.resumeToken,
        // From the server, never re-typed: recovery restores who they
        // already are, and asking them to say it again would invite a
        // mismatch with the name on the host's board.
        displayName: session.displayName ?? "",
        preferredLanguage: session.preferredLanguage ?? "en"
      });
    }
  });
}
