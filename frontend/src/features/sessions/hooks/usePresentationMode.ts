import { useCallback, useEffect, useRef, useState } from "react";
import { usePresentationStore } from "@/features/sessions/presentationStore";

/** A screen wake-lock sentinel, absent from lib.dom in our TS target. */
interface WakeLockSentinelLike {
  release: () => Promise<void>;
}

/**
 * Host presentation mode: the clean projection layout plus, when the
 * browser grants it, real fullscreen and a screen wake lock.
 *
 * The layout (`active`) and `document.fullscreenElement` are deliberately
 * separate states: fullscreen needs a user gesture and can be denied or
 * left with Escape, while the chrome-free layout is useful either way.
 * When the browser *does* grant fullscreen and the host later leaves it
 * (Escape), the UI detects the `fullscreenchange` and restores the normal
 * layout — the two exits stay in sync. When fullscreen was denied, no
 * such sync applies and a hint asks the host to use the browser's own
 * shortcut.
 *
 * The wake lock is progressive enhancement only: requested on entry,
 * re-requested when the tab becomes visible again (the platform releases
 * it on tab switch), released on exit, and silently absent when
 * unsupported — a projector staying awake must never become a blocker.
 */
export function usePresentationMode() {
  const active = usePresentationStore((state) => state.active);
  const setActive = usePresentationStore((state) => state.setActive);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [fullscreenUnavailable, setFullscreenUnavailable] = useState(false);
  // True only while fullscreen was actually granted for this entry —
  // Escape-detection must not fire in the denied/fallback case.
  const grantedRef = useRef(false);
  const wakeLockRef = useRef<WakeLockSentinelLike | null>(null);

  const requestWakeLock = useCallback(async () => {
    try {
      const wakeLock = (
        navigator as { wakeLock?: { request: (type: "screen") => Promise<WakeLockSentinelLike> } }
      ).wakeLock;
      if (wakeLock) {
        wakeLockRef.current = await wakeLock.request("screen");
      }
    } catch {
      // Unsupported or refused — presentation works without it.
    }
  }, []);

  const releaseWakeLock = useCallback(() => {
    void wakeLockRef.current?.release().catch(() => undefined);
    wakeLockRef.current = null;
  }, []);

  const requestFullscreen = useCallback(async () => {
    try {
      if (typeof document.documentElement.requestFullscreen !== "function") {
        throw new Error("Fullscreen API unavailable");
      }
      await document.documentElement.requestFullscreen();
      grantedRef.current = true;
      setFullscreenUnavailable(false);
    } catch {
      grantedRef.current = false;
      setFullscreenUnavailable(true);
    }
  }, []);

  /** Must be called from a user gesture — the fullscreen request rides it. */
  const enter = useCallback(async () => {
    setActive(true);
    await requestFullscreen();
    await requestWakeLock();
  }, [setActive, requestFullscreen, requestWakeLock]);

  const exit = useCallback(() => {
    setActive(false);
    setFullscreenUnavailable(false);
    grantedRef.current = false;
    releaseWakeLock();
    if (document.fullscreenElement) {
      void document.exitFullscreen().catch(() => undefined);
    }
  }, [setActive, releaseWakeLock]);

  useEffect(() => {
    const onFullscreenChange = () => {
      const inFullscreen = document.fullscreenElement != null;
      setIsFullscreen(inFullscreen);
      // Escape (or the browser UI) left fullscreen: restore normal layout.
      if (!inFullscreen && grantedRef.current) {
        grantedRef.current = false;
        setActive(false);
        releaseWakeLock();
      }
    };
    document.addEventListener("fullscreenchange", onFullscreenChange);
    return () => document.removeEventListener("fullscreenchange", onFullscreenChange);
  }, [setActive, releaseWakeLock]);

  useEffect(() => {
    if (!active) {
      return;
    }
    const onVisible = () => {
      if (document.visibilityState === "visible") {
        void requestWakeLock();
      }
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [active, requestWakeLock]);

  // Route change unmounts the session page: keep the stored layout for a
  // same-session return, but never leave a wake lock behind.
  useEffect(() => releaseWakeLock, [releaseWakeLock]);

  return {
    active,
    isFullscreen,
    /** Fullscreen was denied/unsupported — the layout still applies. */
    fullscreenUnavailable,
    enter,
    exit,
    /** Re-request after a refresh restored the layout without fullscreen. */
    requestFullscreen
  };
}
