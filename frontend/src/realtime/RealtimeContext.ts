import { createContext } from "react";
import type { RealtimeClient } from "@/realtime/RealtimeClient";

export const RealtimeContext = createContext<RealtimeClient | null>(null);
