import type { Client, IMessage, StompConfig, messageCallbackType } from "@stomp/stompjs";
import { RealtimeClient } from "@/realtime/RealtimeClient";
import { useConnectionStore } from "@/realtime/connectionStore";
import type { ProtocolMessage, ProtocolMessageType } from "@/types/protocol";

/**
 * A scriptable stand-in for the STOMP client (PR #1's fake transport,
 * extracted from RealtimeClient.test.ts once the lobby tests needed it
 * too): the test drives connect, disconnect, and incoming frames by hand.
 */
export class FakeStomp {
  config!: StompConfig;
  connected = false;
  readonly subscriptions = new Map<string, messageCallbackType>();
  readonly unsubscribed: string[] = [];

  asClient(): Client {
    return this as unknown as Client;
  }

  activate(): void {
    // Connection completes only when the test calls simulateConnect().
  }

  async deactivate(): Promise<void> {
    this.connected = false;
  }

  subscribe(destination: string, callback: messageCallbackType) {
    this.subscriptions.set(destination, callback);
    return {
      id: destination,
      unsubscribe: () => {
        this.subscriptions.delete(destination);
        this.unsubscribed.push(destination);
      }
    };
  }

  simulateConnect(): void {
    this.connected = true;
    this.config.onConnect?.({} as never);
  }

  simulateConnectionLost(): void {
    this.connected = false;
    this.subscriptions.clear();
    this.config.onWebSocketClose?.({} as never);
  }

  deliver(destination: string, message: ProtocolMessage): void {
    this.subscriptions.get(destination)?.({ body: JSON.stringify(message) } as IMessage);
  }
}

/** A well-formed RFC-005 envelope for tests. */
export function protocolMessage(
  type: ProtocolMessageType,
  sessionId = "s-1",
  payload?: unknown
): ProtocolMessage {
  return {
    protocolVersion: 1,
    messageId: `m-${Math.random().toString(36).slice(2)}`,
    sessionId,
    occurredAt: new Date().toISOString(),
    type,
    payload
  };
}

/**
 * A RealtimeClient wired to a FakeStomp, for injecting into renderApp.
 * Mirrors state into the connection store the same way the app's own
 * RealtimeProvider construction does, so ConnectionIndicator behaves.
 */
export function fakeRealtimeClient(): { client: RealtimeClient; fake: FakeStomp } {
  const fake = new FakeStomp();
  const client = new RealtimeClient({
    url: "ws://test/ws/websocket",
    onStateChange: (state) => useConnectionStore.getState().setStatus(state),
    createStompClient: (config) => {
      fake.config = config;
      return fake.asClient();
    }
  });
  return { client, fake };
}
