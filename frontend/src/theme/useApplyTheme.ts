import { useEffect } from "react";
import { useUiPreferencesStore, type ThemePreference } from "@/theme/uiPreferencesStore";

function resolve(preference: ThemePreference, systemPrefersDark: boolean): "light" | "dark" {
  if (preference === "system") {
    return systemPrefersDark ? "dark" : "light";
  }
  return preference;
}

/**
 * Stamps the resolved theme onto <html data-theme="...">, which flips the
 * token set in theme/tokens.css. Tracks the OS preference live while the
 * user's choice is "system". Mounted once in Providers.
 */
export function useApplyTheme(): void {
  const preference = useUiPreferencesStore((state) => state.theme);

  useEffect(() => {
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const apply = () => {
      document.documentElement.dataset.theme = resolve(preference, media.matches);
    };
    apply();
    media.addEventListener("change", apply);
    return () => media.removeEventListener("change", apply);
  }, [preference]);
}
