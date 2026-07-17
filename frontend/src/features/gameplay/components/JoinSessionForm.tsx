import { useForm } from "react-hook-form";
import { z } from "zod";
import { errorMessage } from "@/api/apiError";
import { Button } from "@/components/common/Button";
import { FormField } from "@/components/forms/FormField";
import { displayNameSchema, languageCodeSchema, sessionPinSchema, zodForm } from "@/utils/validation";

const LANGUAGES = [
  { value: "en", label: "English" },
  { value: "kn", label: "Kannada" },
  { value: "hi", label: "Hindi" },
  { value: "ta", label: "Tamil" },
  { value: "te", label: "Telugu" },
  { value: "ml", label: "Malayalam" }
];

const joinSchema = z.object({
  pin: sessionPinSchema,
  displayName: displayNameSchema,
  preferredLanguage: languageCodeSchema
});

export type JoinSessionFormValues = z.infer<typeof joinSchema>;

export interface JoinSessionFormProps {
  /** When set, the PIN is already known (arrived via /play/:pin) and is shown read-only. */
  fixedPin?: string;
  onSubmit: (values: JoinSessionFormValues) => Promise<void>;
  isSubmitting?: boolean;
  error?: unknown;
}

/** The PIN + nickname + language form — the one entry point to gameplay for a participant. */
export function JoinSessionForm({ fixedPin, onSubmit, isSubmitting, error }: JoinSessionFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors }
  } = useForm<JoinSessionFormValues>(
    zodForm(joinSchema, { defaultValues: { pin: fixedPin ?? "", preferredLanguage: "en" } })
  );

  const submit = handleSubmit(async (values) => {
    await onSubmit(values);
  });

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-4">
      {fixedPin ? (
        <div className="flex flex-col gap-1.5">
          <span className="text-sm font-medium">Session code</span>
          <span className="font-mono text-2xl font-bold tracking-[0.3em]">{fixedPin}</span>
          <input type="hidden" value={fixedPin} {...register("pin")} />
        </div>
      ) : (
        <FormField
          label="Session code"
          placeholder="042317"
          inputMode="numeric"
          error={errors.pin?.message}
          {...register("pin")}
        />
      )}

      <FormField
        label="Your name"
        placeholder="Aman"
        error={errors.displayName?.message}
        {...register("displayName")}
      />

      <div className="flex flex-col gap-1.5">
        <label htmlFor="preferredLanguage" className="text-sm font-medium">
          Language
        </label>
        <select
          id="preferredLanguage"
          className="h-10 rounded-md border border-input bg-background px-3 text-sm"
          {...register("preferredLanguage")}
        >
          {LANGUAGES.map((language) => (
            <option key={language.value} value={language.value}>
              {language.label}
            </option>
          ))}
        </select>
        {errors.preferredLanguage && (
          <p role="alert" className="text-sm text-destructive">
            {errors.preferredLanguage.message}
          </p>
        )}
      </div>

      {error != null && (
        <p role="alert" className="text-sm text-destructive">
          {errorMessage(error)}
        </p>
      )}

      <Button type="submit" isLoading={isSubmitting}>
        Join
      </Button>
    </form>
  );
}
