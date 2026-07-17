import { useEffect, useState, type ReactNode } from "react";
import { RealtimeClient } from "@/realtime/RealtimeClient";
import { RealtimeContext } from "@/realtime/RealtimeContext";
import { useConnectionStore } from "@/realtime/connectionStore";
import { env } from "@/utils/env";

/**
 * Owns the app's single RealtimeClient and mirrors its connection state
 * into the connection store. Deliberately does NOT connect on mount:
 * realtime is only needed while a session is live, so the feature that
 * needs it calls connect() (and gameplay PRs will own that lifecycle).
 */
export function RealtimeProvider({
  children,
  client
}: {
  children: ReactNode;
  /** Test seam: inject a preconfigured client. */
  client?: RealtimeClient;
}) {
  const setStatus = useConnectionStore((state) => state.setStatus);
  const [realtimeClient] = useState(
    () =>
      client ??
      new RealtimeClient({
        url: env.wsUrl(),
        onStateChange: (state) => useConnectionStore.getState().setStatus(state)
      })
  );

  useEffect(() => {
    return () => {
      void realtimeClient.disconnect();
      setStatus("disconnected");
    };
  }, [realtimeClient, setStatus]);

  return <RealtimeContext.Provider value={realtimeClient}>{children}</RealtimeContext.Provider>;
}
