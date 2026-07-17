import type { ReactNode } from "react";
import { Spinner } from "@/components/common/Spinner";

export interface LoadingOverlayProps {
  /** Covers its children with a translucent layer while true. */
  isLoading: boolean;
  children: ReactNode;
}

export function LoadingOverlay({ isLoading, children }: LoadingOverlayProps) {
  return (
    <div className="relative">
      {children}
      {isLoading && (
        <div
          aria-busy="true"
          className="absolute inset-0 z-10 flex items-center justify-center rounded-md bg-background/60"
        >
          <Spinner size="lg" className="text-primary" />
        </div>
      )}
    </div>
  );
}
