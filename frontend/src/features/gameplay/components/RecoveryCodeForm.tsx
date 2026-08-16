import { useState } from "react";
import { errorMessage, isApiClientError } from "@/api/apiError";
import { Button } from "@/components/common/Button";
import { FormField } from "@/components/forms/FormField";

export interface RecoveryCodeFormProps {
  onSubmit: (code: string) => Promise<unknown>;
  isSubmitting: boolean;
  error: unknown;
}

/**
 * Where a player types the six digits the Quiz Master read out.
 *
 * The last step of the only recovery path that exists for someone whose
 * browser storage is gone. It deliberately asks for a code and nothing
 * else — not their name, which proves nothing, and not their score, which
 * they should never be asked to assert.
 */
export function RecoveryCodeForm({ onSubmit, isSubmitting, error }: RecoveryCodeFormProps) {
  const [code, setCode] = useState("");
  const digits = code.replace(/\D/g, "").slice(0, 6);

  return (
    <form
      className="flex flex-col gap-3"
      onSubmit={(event) => {
        event.preventDefault();
        void onSubmit(digits);
      }}
    >
      <FormField
        label="Recovery code"
        placeholder="482731"
        inputMode="numeric"
        autoComplete="one-time-code"
        value={code}
        onChange={(event) => setCode(event.target.value)}
      />
      {error != null && (
        <p role="alert" className="text-sm text-destructive">
          {isApiClientError(error) && error.status === 429
            ? "Too many attempts. Wait a moment, then ask the Quiz Master for a new code."
            : errorMessage(error)}
        </p>
      )}
      <Button type="submit" disabled={digits.length !== 6} isLoading={isSubmitting}>
        Rejoin my game
      </Button>
    </form>
  );
}
