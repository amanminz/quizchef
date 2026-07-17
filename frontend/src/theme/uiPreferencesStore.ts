import { create } from "zustand";
import { persist } from "zustand/middleware";

export type ThemePreference = "light" | "dark" | "system";

/**
 * UI preferences — pure client state, so Zustand owns it (RFC-009 state
 * ownership). Persisted so the choice survives a refresh.
 */
interface UiPreferencesState {
  theme: ThemePreference;
  setTheme: (theme: ThemePreference) => void;
}

export const useUiPreferencesStore = create<UiPreferencesState>()(
  persist(
    (set) => ({
      theme: "system",
      setTheme: (theme) => set({ theme })
    }),
    { name: "quizchef.ui.v1" }
  )
);
