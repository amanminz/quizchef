import axios, { AxiosError, AxiosHeaders } from "axios";
import { ApiClientError } from "@/api/apiError";
import { useAuthStore } from "@/auth/authStore";
import type { ApiErrorBody } from "@/types/api";
import { env } from "@/utils/env";

/**
 * The single HTTP client. Centralizes the base URL, JWT injection, timeout,
 * and error mapping — components never import axios and never see a raw
 * AxiosError. Retries are deliberately NOT configured here: TanStack Query
 * owns the retry policy (Providers.tsx), so requests are never retried twice
 * at two layers.
 */
export const apiClient = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: 10_000,
  headers: { "Content-Type": "application/json" }
});

apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    const headers = AxiosHeaders.from(config.headers);
    headers.set("Authorization", `Bearer ${token}`);
    config.headers = headers;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorBody>) => {
    throw toApiClientError(error);
  }
);

function toApiClientError(error: AxiosError<ApiErrorBody>): ApiClientError {
  const status = error.response?.status ?? null;
  const body = error.response?.data;

  // A rejected token means the server-side identity session is gone
  // (revoked or expired — RFC-002). Clear local auth so RequireAuth routes
  // back to the login page. A failed *login attempt* is not an expiry.
  if (status === 401 && !isAuthenticationAttempt(error)) {
    useAuthStore.getState().expireSession();
  }

  if (body?.code && body.message) {
    return new ApiClientError(
      body.code,
      body.message,
      status,
      body.fieldErrors ?? [],
      body.correlationId ?? null
    );
  }
  if (status !== null) {
    return new ApiClientError(`http.${status}`, `Request failed with status ${status}`, status);
  }
  if (error.code === "ECONNABORTED") {
    return new ApiClientError("network.timeout", "The server took too long to respond.", null);
  }
  return new ApiClientError("network.unavailable", "Cannot reach the server.", null);
}

function isAuthenticationAttempt(error: AxiosError): boolean {
  return error.config?.url?.includes("/auth/") ?? false;
}
