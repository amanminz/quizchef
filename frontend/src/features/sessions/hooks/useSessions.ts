import { useQueries } from "@tanstack/react-query";
import { useEffect, useMemo } from "react";
import { isApiClientError } from "@/api/apiError";
import { sessionApi } from "@/api/sessionApi";
import { useHostedSessionsStore } from "@/features/sessions/hostedSessionsStore";
import { sessionKeys } from "@/features/sessions/queryKeys";
import type { SessionSummaryResponse } from "@/types/api";

/**
 * The sessions dashboard's data: every locally tracked hosted session,
 * hydrated through its own detail query (the backend has no session list
 * endpoint — see hostedSessionsStore). A 404 means the session expired
 * from the server's Redis-backed store, so the id is pruned from the
 * registry rather than surfaced as an error; any other failure is a real
 * error and is reported.
 */
export function useSessions() {
  const sessionIds = useHostedSessionsStore((state) => state.sessionIds);
  const remove = useHostedSessionsStore((state) => state.remove);

  const queries = useQueries({
    queries: sessionIds.map((sessionId) => ({
      queryKey: sessionKeys.detail(sessionId),
      queryFn: () => sessionApi.getById(sessionId)
    }))
  });

  const expiredIds = useMemo(
    () =>
      sessionIds.filter((_, index) => {
        const error = queries[index]?.error;
        return isApiClientError(error) && error.status === 404;
      }),
    [queries, sessionIds]
  );

  useEffect(() => {
    expiredIds.forEach((sessionId) => remove(sessionId));
  }, [expiredIds, remove]);

  const sessions = queries
    .map((query) => query.data)
    .filter((session): session is SessionSummaryResponse => session !== undefined);

  const firstRealError = queries.find(
    (query) => query.error && !(isApiClientError(query.error) && query.error.status === 404)
  )?.error;

  return {
    sessions,
    isPending: queries.some((query) => query.isPending),
    error: firstRealError ?? null,
    refetch: () => queries.forEach((query) => void query.refetch())
  };
}
