import type { ReactNode } from "react";
import { cn } from "@/utils/cn";

export interface PageContainerProps {
  children: ReactNode;
  className?: string;
}

/** The standard page width and padding for all routed content. */
export function PageContainer({ children, className }: PageContainerProps) {
  return (
    <div className={cn("mx-auto w-full max-w-5xl px-4 py-8 sm:px-6", className)}>{children}</div>
  );
}
