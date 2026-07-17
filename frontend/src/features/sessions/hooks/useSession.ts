import { useQuery } from "@tanstack/react-query";
import { sessionApi } from "@/api/sessionApi";
import { sessionKeys } from "@/features/sessions/queryKeys";

/** One session's summary — metadata, lifecycle state, roster size, settings. */
export function useSession(sessionId: string | undefined) {
  return useQuery({
    queryKey: sessionKeys.detail(sessionId ?? ""),
    queryFn: () => sessionApi.getById(sessionId!),
    enabled: sessionId !== undefined
  });
}
