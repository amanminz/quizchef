import { Maximize2, Minimize2 } from "lucide-react";
import { Button } from "@/components/common/Button";
import type { usePresentationMode } from "@/features/sessions/hooks/usePresentationMode";

export type PresentationController = ReturnType<typeof usePresentationMode>;

/**
 * The host's presentation-mode switch. In normal layout: a prominent
 * "Enter Presentation Mode" action. In presentation layout: an
 * unobtrusive exit, plus the fullscreen fallback hint when the browser
 * refused the request, and a re-enter-fullscreen action after a refresh
 * restored the layout without fullscreen (a fresh gesture is required).
 */
export function PresentationToggle({ presentation }: { presentation: PresentationController }) {
  if (!presentation.active) {
    return (
      <Button variant="secondary" onClick={() => void presentation.enter()}>
        <Maximize2 aria-hidden className="h-4 w-4" />
        Enter Presentation Mode
      </Button>
    );
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <div className="flex items-center gap-2">
        {!presentation.isFullscreen && !presentation.fullscreenUnavailable && (
          <Button variant="ghost" size="sm" onClick={() => void presentation.requestFullscreen()}>
            <Maximize2 aria-hidden className="h-4 w-4" />
            Enter fullscreen
          </Button>
        )}
        <Button variant="ghost" size="sm" onClick={presentation.exit}>
          <Minimize2 aria-hidden className="h-4 w-4" />
          Exit Presentation Mode
        </Button>
      </div>
      {presentation.fullscreenUnavailable && (
        <p className="text-xs text-muted-foreground" role="status">
          Presentation layout enabled. Use your browser's fullscreen shortcut for the best view.
        </p>
      )}
    </div>
  );
}
