import { useEffect } from "react";

/**
 * Sets the browser tab to "<title> | QuizChef" while the component is
 * mounted and the title known, and restores the previous document title
 * on unmount — so leaving a session returns the tab to normal.
 */
export function useDocumentTitle(title: string | undefined) {
  useEffect(() => {
    if (!title) {
      return;
    }
    const previous = document.title;
    document.title = `${title} | QuizChef`;
    return () => {
      document.title = previous;
    };
  }, [title]);
}
