import type { ApiFieldError } from "@/types/api";

/**
 * The one error shape the rest of the frontend sees. Every axios failure is
 * mapped to this in the response interceptor, so callers switch on the
 * backend's stable error `code` (RFC-002/003/004) — never on transport
 * details.
 */
export class ApiClientError extends Error {
  readonly code: string;
  /** HTTP status, or null when the request never reached the server. */
  readonly status: number | null;
  readonly fieldErrors: ApiFieldError[];
  /**
   * The backend's request correlation id (Phase 3 PR #2), so a fatal-error
   * dialog can show a reference a support report can match back to server
   * logs. Null when the request never reached the server.
   */
  readonly correlationId: string | null;

  constructor(
    code: string,
    message: string,
    status: number | null,
    fieldErrors: ApiFieldError[] = [],
    correlationId: string | null = null
  ) {
    super(message);
    this.name = "ApiClientError";
    this.code = code;
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.correlationId = correlationId;
  }
}

export function isApiClientError(error: unknown): error is ApiClientError {
  return error instanceof ApiClientError;
}

/** A human-readable message for any error, with a safe fallback. */
export function errorMessage(error: unknown): string {
  if (isApiClientError(error)) {
    return error.message;
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return "Something went wrong. Please try again.";
}
