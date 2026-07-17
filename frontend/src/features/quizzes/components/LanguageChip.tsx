import { cn } from "@/utils/cn";

export interface LanguageChipProps {
  /** BCP-47 tag, e.g. "en", "kn". */
  language: string;
  className?: string;
}

/** A small chip naming an available language — content availability, not a badge of status. */
export function LanguageChip({ language, className }: LanguageChipProps) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded border border-border px-1.5 py-0.5 text-[11px] font-medium uppercase text-muted-foreground",
        className
      )}
    >
      {language}
    </span>
  );
}
