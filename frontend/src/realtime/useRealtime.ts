import { useContext, useEffect } from "react";
import type { RealtimeClient, RealtimeMessageHandler } from "@/realtime/RealtimeClient";
import { RealtimeContext } from "@/realtime/RealtimeContext";
import { useConnectionStore } from "@/realtime/connectionStore";

/**
 * The hook layer between components and the RealtimeClient (RFC-009):
 * components call these; they never import STOMP or build destinations.
 */
export function useRealtimeClient(): RealtimeClient {
  const context = useContext(RealtimeContext);
  if (!context) {
    throw new Error("useRealtimeClient must be used within RealtimeProvider");
  }
  return context;
}

export function useRealtime() {
  const client = useRealtimeClient();
  const status = useConnectionStore((state) => state.status);

  return {
    status,
    connect: () => client.connect(),
    disconnect: () => client.disconnect()
  };
}

/**
 * Subscribes to a destination for the lifetime of the component. The
 * subscription survives reconnects (RealtimeClient resubscribes) and is
 * removed on unmount.
 */
export function useRealtimeSubscription(
  destination: string | null,
  handler: RealtimeMessageHandler
): void {
  const client = useRealtimeClient();

  useEffect(() => {
    if (!destination) {
      return;
    }
    return client.subscribe(destination, handler);
    // Callers pass stable handlers (useCallback); destination changes resubscribe.
  }, [client, destination, handler]);
}
