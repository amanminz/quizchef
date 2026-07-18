import { useQuery } from "@tanstack/react-query";
import { sessionApi } from "@/api/sessionApi";
import { sessionKeys } from "@/features/sessions/queryKeys";

/**
 * The host's roster read — every joined name in stable join order. The
 * realtime join/disconnect/reconnect events are the notification; this
 * read is the truth the projected wall renders (events deliberately carry
 * only participant ids). Host-only server-side; enable it only on host
 * screens.
 */
export function useRoster(sessionId: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: sessionKeys.roster(sessionId ?? ""),
    queryFn: () => sessionApi.participants(sessionId!),
    enabled: sessionId !== undefined && enabled
  });
}
