import { Client, type IMessage, type StompConfig, type StompSubscription } from "@stomp/stompjs";
import { PROTOCOL_VERSION, type ProtocolMessage } from "@/types/protocol";

export type RealtimeConnectionState = "disconnected" | "connecting" | "connected" | "reconnecting";

export type RealtimeMessageHandler = (message: ProtocolMessage) => void;

export interface RealtimeClientOptions {
  /** WebSocket URL of the RFC-005 endpoint. */
  url: string;
  onStateChange?: (state: RealtimeConnectionState) => void;
  /** Delay before an automatic reconnect attempt. */
  reconnectDelayMillis?: number;
  /** Outgoing/incoming heartbeat interval. */
  heartbeatMillis?: number;
  /** Test seam: build the underlying STOMP client from a config. */
  createStompClient?: (config: StompConfig) => Client;
}

/**
 * The one place the frontend speaks STOMP — the realtime mirror of the
 * axios client. Components and hooks call subscribe/connect/disconnect and
 * receive parsed RFC-005 ProtocolMessages; they never see a STOMP frame, a
 * destination string convention, or a WebSocket. Swapping the transport
 * (SSE, MQTT) would change this file only.
 *
 * Subscriptions are durable across the connection lifecycle: handlers
 * registered while disconnected activate on connect, and every destination
 * is resubscribed automatically after a dropped connection reconnects.
 */
export class RealtimeClient {
  private readonly stomp: Client;
  private readonly onStateChange?: (state: RealtimeConnectionState) => void;
  private readonly handlers = new Map<string, Set<RealtimeMessageHandler>>();
  private readonly active = new Map<string, StompSubscription>();
  private state: RealtimeConnectionState = "disconnected";
  private wantConnected = false;

  constructor(options: RealtimeClientOptions) {
    this.onStateChange = options.onStateChange;
    const config: StompConfig = {
      brokerURL: options.url,
      reconnectDelay: options.reconnectDelayMillis ?? 2_000,
      heartbeatIncoming: options.heartbeatMillis ?? 10_000,
      heartbeatOutgoing: options.heartbeatMillis ?? 10_000,
      onConnect: () => {
        this.setState("connected");
        this.resubscribeAll();
      },
      onWebSocketClose: () => {
        this.active.clear();
        this.setState(this.wantConnected ? "reconnecting" : "disconnected");
      }
    };
    this.stomp = options.createStompClient?.(config) ?? new Client(config);
  }

  connectionState(): RealtimeConnectionState {
    return this.state;
  }

  connect(): void {
    if (this.wantConnected) {
      return;
    }
    this.wantConnected = true;
    this.setState("connecting");
    this.stomp.activate();
  }

  async disconnect(): Promise<void> {
    this.wantConnected = false;
    this.active.clear();
    await this.stomp.deactivate();
    this.setState("disconnected");
  }

  /**
   * Registers a handler for a destination and returns its unsubscribe
   * function. Safe to call in any connection state.
   */
  subscribe(destination: string, handler: RealtimeMessageHandler): () => void {
    let handlersForDestination = this.handlers.get(destination);
    if (!handlersForDestination) {
      handlersForDestination = new Set();
      this.handlers.set(destination, handlersForDestination);
    }
    handlersForDestination.add(handler);

    if (this.stomp.connected && !this.active.has(destination)) {
      this.subscribeOnBroker(destination);
    }

    return () => {
      handlersForDestination.delete(handler);
      if (handlersForDestination.size === 0) {
        this.handlers.delete(destination);
        const subscription = this.active.get(destination);
        this.active.delete(destination);
        if (subscription && this.stomp.connected) {
          subscription.unsubscribe();
        }
      }
    };
  }

  private resubscribeAll(): void {
    for (const destination of this.handlers.keys()) {
      if (!this.active.has(destination)) {
        this.subscribeOnBroker(destination);
      }
    }
  }

  private subscribeOnBroker(destination: string): void {
    const subscription = this.stomp.subscribe(destination, (frame: IMessage) => {
      const message = this.parse(frame);
      if (!message) {
        return;
      }
      this.handlers.get(destination)?.forEach((handler) => handler(message));
    });
    this.active.set(destination, subscription);
  }

  private parse(frame: IMessage): ProtocolMessage | null {
    try {
      const message = JSON.parse(frame.body) as ProtocolMessage;
      if (message.protocolVersion !== PROTOCOL_VERSION) {
        // A newer protocol is not an error — log and deliver; RFC-005 bumps
        // the version only on breaking changes, at which point this client
        // updates deliberately.
        console.warn(
          `Realtime message with protocol version ${message.protocolVersion}; this client speaks ${PROTOCOL_VERSION}`
        );
      }
      return message;
    } catch {
      console.error("Discarding unparseable realtime message");
      return null;
    }
  }

  private setState(state: RealtimeConnectionState): void {
    if (this.state !== state) {
      this.state = state;
      this.onStateChange?.(state);
    }
  }
}
