import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Merges Tailwind class lists with conflict resolution — the one way
 * components combine their base classes with caller overrides.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
