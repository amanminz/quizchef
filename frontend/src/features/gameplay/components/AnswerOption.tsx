import { Check } from "lucide-react";
import { cn } from "@/utils/cn";

export interface AnswerOptionProps {
  text: string;
  selected: boolean;
  disabled?: boolean;
  onToggle: () => void;
}

/**
 * One selectable option — a native `<button>` so keyboard operation (Tab,
 * Enter, Space) works with no custom key handling, `aria-pressed` carries
 * the toggle state for assistive tech.
 */
export function AnswerOption({ text, selected, disabled, onToggle }: AnswerOptionProps) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      disabled={disabled}
      onClick={onToggle}
      className={cn(
        "flex items-center justify-between gap-3 rounded-md border px-4 py-3 text-left text-sm font-medium transition-colors",
        "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
        "disabled:pointer-events-none disabled:opacity-50",
        selected ? "border-primary bg-primary/5" : "hover:bg-muted"
      )}
    >
      <span>{text}</span>
      {selected && <Check aria-hidden className="h-4 w-4 shrink-0 text-primary" />}
    </button>
  );
}
