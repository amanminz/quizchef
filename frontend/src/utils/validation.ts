import { zodResolver } from "@hookform/resolvers/zod";
import type { UseFormProps } from "react-hook-form";
import { z } from "zod";

/**
 * Shared field schemas, mirroring the backend's validation rules (RFC-002,
 * RFC-004) so users see the same limits the server enforces. The server
 * remains the authority — these only give faster feedback.
 */
export const emailSchema = z
  .string()
  .trim()
  .min(1, "Email is required")
  .email("Enter a valid email");

export const passwordSchema = z
  .string()
  .min(8, "Password must be at least 8 characters")
  .max(128, "Password must be at most 128 characters");

export const sessionPinSchema = z
  .string()
  .trim()
  .regex(/^\d{6}$/, "A session PIN is exactly 6 digits");

export const displayNameSchema = z
  .string()
  .trim()
  .min(1, "Display name is required")
  .max(50, "Display name must be at most 50 characters");

export const titleSchema = z.string().trim().min(1, "Title is required").max(200, "Title is too long");

/** A BCP-47 language tag as the backend's LanguageCode accepts (language[-Script][-REGION]). */
export const languageCodeSchema = z
  .string()
  .trim()
  .regex(/^[a-zA-Z]{2,3}(-[a-zA-Z]{4})?(-[a-zA-Z]{2})?$/, "Enter a valid language tag, e.g. en or kn");

/**
 * Standard react-hook-form options for a zod-validated form: schema-driven
 * resolver, validate on submit, re-validate on change.
 */
export function zodForm<TShape extends z.ZodRawShape>(
  schema: z.ZodObject<TShape>,
  options?: Omit<UseFormProps<z.infer<z.ZodObject<TShape>>>, "resolver">
): UseFormProps<z.infer<z.ZodObject<TShape>>> {
  return {
    resolver: zodResolver(schema),
    mode: "onSubmit",
    reValidateMode: "onChange",
    ...options
  };
}
