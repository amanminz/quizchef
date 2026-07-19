import { useEffect } from "react";
import { SITE_TITLE } from "@/utils/branding";

/**
 * Sets the browser tab to "<title> | <site brand>" while the component is
 * mounted and the title known, and restores the previous document title
 * on unmount — so leaving a session returns the tab to normal.
 */
export function useDocumentTitle(title: string | undefined) {
  useEffect(() => {
    if (!title) {
      return;
    }
    const previous = document.title;
    document.title = `${title} | ${SITE_TITLE}`;
    return () => {
      document.title = previous;
    };
  }, [title]);
}
