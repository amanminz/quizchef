import { http, HttpResponse } from "msw";
import type { ApiErrorBody, CurrentUserResponse, LoginResponse } from "@/types/api";

export const testIdentity = {
  email: "host@example.com",
  password: "StrongPassword@123",
  token: "test-jwt-token",
  identityId: "1557e119-29db-4e3a-b3ed-ea47233e8a59"
};

const loginResponse: LoginResponse = {
  identityId: testIdentity.identityId,
  displayName: "Test Host",
  token: testIdentity.token,
  expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
  authorities: ["USER", "QUIZ_MASTER"]
};

const currentUserResponse: CurrentUserResponse = {
  identityId: testIdentity.identityId,
  identityType: "REGISTERED",
  roles: ["USER", "QUIZ_MASTER"],
  permissions: ["QUIZ_VIEW", "QUIZ_CREATE", "QUIZ_EDIT", "QUIZ_HOST"]
};

export function apiError(code: string, message: string): ApiErrorBody {
  return { code, message, timestamp: new Date().toISOString(), fieldErrors: [] };
}

/** The happy-path API used by most tests; individual tests override per-case. */
export const handlers = [
  http.post("/api/v1/auth/login", async ({ request }) => {
    const body = (await request.json()) as { email?: string; password?: string };
    if (body.email === testIdentity.email && body.password === testIdentity.password) {
      return HttpResponse.json(loginResponse);
    }
    return HttpResponse.json(apiError("auth.unauthorized", "Invalid email or password"), {
      status: 401
    });
  }),

  http.get("/api/v1/users/me", ({ request }) => {
    if (request.headers.get("Authorization") !== `Bearer ${testIdentity.token}`) {
      return HttpResponse.json(apiError("identity.token.invalid", "Token is not valid"), {
        status: 401
      });
    }
    return HttpResponse.json(currentUserResponse);
  })
];
