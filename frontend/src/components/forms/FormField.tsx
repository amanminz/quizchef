import { useId, type ComponentProps } from "react";
import { cn } from "@/utils/cn";

export interface FormFieldProps extends Omit<ComponentProps<"input">, "id"> {
  label: string;
  /** Validation message from react-hook-form's field error. */
  error?: string;
}

/**
 * A labeled input with error display, wired for react-hook-form via
 * {...register("name")} spread. Presentation only — validation rules live
 * in zod schemas (utils/validation.ts).
 */
export function FormField({ label, error, className, ...inputProps }: FormFieldProps) {
  const id = useId();
  const errorId = `${id}-error`;

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        className={cn(
          "h-10 rounded-md border border-input bg-background px-3 text-sm",
          "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
          error && "border-destructive focus-visible:ring-destructive",
          className
        )}
        {...inputProps}
      />
      {error && (
        <p id={errorId} role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}
