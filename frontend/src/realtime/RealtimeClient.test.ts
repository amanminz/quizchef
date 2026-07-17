import type { Client, IMessage, StompConfig, messageCallbackType } from "@stomp/stompjs";
import { describe, expect, it, vi } from "vitest";
import { RealtimeClient, type RealtimeConnectionState } from "@/realtime/RealtimeClient";
import type { ProtocolMessage } from "@/types/protocol";

/**
 * A scriptable stand-in for the STOMP client: the test drives connect,
 * disconnect, and incoming frames by hand.
 */
class FakeStomp {
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

function protocolMessage(type: ProtocolMessage["type"]): ProtocolMessage {
  return {
    protocolVersion: 1,
    messageId: "m-1",
    sessionId: "s-1",
    occurredAt: new Date().toISOString(),
    type
  };
}

function createClient() {
  const fake = new FakeStomp();
  const states: RealtimeConnectionState[] = [];
  const client = new RealtimeClient({
    url: "ws://test/ws/websocket",
    onStateChange: (state) => states.push(state),
    createStompClient: (config) => {
      fake.config = config;
      return fake.asClient();
    }
  });
  return { client, fake, states };
}

describe("RealtimeClient", () => {
  it("walks connecting → connected and back to disconnected", async () => {
    const { client, fake, states } = createClient();

    client.connect();
    fake.simulateConnect();
    await client.disconnect();

    expect(states).toEqual(["connecting", "connected", "disconnected"]);
    expect(client.connectionState()).toBe("disconnected");
  });

  it("activates subscriptions made before the connection exists", () => {
    const { client, fake } = createClient();
    const handler = vi.fn();

    client.subscribe("/topic/session/s-1", handler);
    expect(fake.subscriptions.size).toBe(0);

    client.connect();
    fake.simulateConnect();
    fake.deliver("/topic/session/s-1", protocolMessage("session.started"));

    expect(handler).toHaveBeenCalledWith(expect.objectContaining({ type: "session.started" }));
  });

  it("delivers parsed protocol messages to every handler on the destination", () => {
    const { client, fake } = createClient();
    const first = vi.fn();
    const second = vi.fn();
    client.connect();
    fake.simulateConnect();

    client.subscribe("/topic/session/s-1", first);
    client.subscribe("/topic/session/s-1", second);
    fake.deliver("/topic/session/s-1", protocolMessage("question.started"));

    expect(first).toHaveBeenCalledTimes(1);
    expect(second).toHaveBeenCalledTimes(1);
  });

  it("unsubscribes from the broker when the last handler leaves", () => {
    const { client, fake } = createClient();
    const handler = vi.fn();
    client.connect();
    fake.simulateConnect();

    const unsubscribe = client.subscribe("/topic/session/s-1", handler);
    unsubscribe();

    expect(fake.unsubscribed).toContain("/topic/session/s-1");
    fake.deliver("/topic/session/s-1", protocolMessage("session.started"));
    expect(handler).not.toHaveBeenCalled();
  });

  it("marks a dropped connection as reconnecting and resubscribes on recovery", () => {
    const { client, fake, states } = createClient();
    const handler = vi.fn();
    client.connect();
    fake.simulateConnect();
    client.subscribe("/topic/session/s-1", handler);

    fake.simulateConnectionLost();
    expect(states.at(-1)).toBe("reconnecting");

    fake.simulateConnect();
    fake.deliver("/topic/session/s-1", protocolMessage("lobby.opened"));

    expect(states.at(-1)).toBe("connected");
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it("discards unparseable frames without breaking the subscription", () => {
    const { client, fake } = createClient();
    const handler = vi.fn();
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);
    client.connect();
    fake.simulateConnect();
    client.subscribe("/topic/session/s-1", handler);

    fake.subscriptions.get("/topic/session/s-1")?.({ body: "not-json" } as IMessage);
    fake.deliver("/topic/session/s-1", protocolMessage("session.finished"));

    expect(handler).toHaveBeenCalledTimes(1);
    consoleError.mockRestore();
  });
});
