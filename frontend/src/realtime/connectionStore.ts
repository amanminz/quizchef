import { create } from "zustand";
import type { RealtimeConnectionState } from "@/realtime/RealtimeClient";

/**
 * Realtime connection status — client-side state, so Zustand owns it
 * (RFC-009 state ownership). Written only by RealtimeProvider; read by any
 * component that shows connectivity.
 */
interface ConnectionState {
  status: RealtimeConnectionState;
  setStatus: (status: RealtimeConnectionState) => void;
}

export const useConnectionStore = create<ConnectionState>((set) => ({
  status: "disconnected",
  setStatus: (status) => set({ status })
}));
