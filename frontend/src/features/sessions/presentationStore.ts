import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

/**
 * Host presentation mode — local UI state only, never backend session
 * state (RFC-009 state ownership: pure client state lives in Zustand).
 * `active` is the clean projection *layout* (chrome hidden, larger type);
 * browser fullscreen is tracked separately by usePresentationMode, because
 * fullscreen can be denied or exited (Escape) while the layout remains
 * useful. Persisted in sessionStorage so a mid-event refresh restores the
 * layout — but never browser fullscreen, which requires a fresh user
 * gesture; the page offers an "Enter fullscreen" action instead.
 */
interface PresentationState {
  active: boolean;
  setActive: (active: boolean) => void;
}

export const usePresentationStore = create<PresentationState>()(
  persist(
    (set) => ({
      active: false,
      setActive: (active) => set({ active })
    }),
    { name: "quizchef.presentation.v1", storage: createJSONStorage(() => sessionStorage) }
  )
);
