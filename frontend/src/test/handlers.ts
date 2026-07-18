import { http, HttpResponse } from "msw";
import type {
  ApiErrorBody,
  CurrentUserResponse,
  LoginResponse,
  QuestionPageResponse,
  QuizPageResponse
} from "@/types/api";

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
  permissions: [
    "QUIZ_VIEW",
    "QUIZ_CREATE",
    "QUIZ_EDIT",
    "QUIZ_HOST",
    "USER_PROFILE_READ",
    "USER_PROFILE_UPDATE"
  ],
  displayName: "Test Host",
  email: testIdentity.email
};

/**
 * A plain member (USER only) — what a fresh registration looks like since
 * roles became durable. Tests exercising onboarding or role-aware UI
 * override the default host response with this via server.use().
 */
export function memberCurrentUser(): CurrentUserResponse {
  return {
    identityId: testIdentity.identityId,
    identityType: "REGISTERED",
    roles: ["USER"],
    permissions: ["QUIZ_VIEW", "USER_PROFILE_READ", "USER_PROFILE_UPDATE"],
    displayName: "Test Member",
    email: testIdentity.email
  };
}

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
  }),

  // Empty-by-default so tests that don't care about quiz/question data
  // never see an unhandled-request warning; override with server.use().
  http.get("/api/v1/quizzes/mine", () =>
    HttpResponse.json(emptyQuizPage())
  ),
  http.get("/api/v1/questions", () => HttpResponse.json(emptyQuestionPage())),
  // Session cards resolve quiz titles by id; tests that care override this.
  // Must stay after /quizzes/mine — MSW matches in order.
  http.get("/api/v1/quizzes/:quizId", () =>
    HttpResponse.json(apiError("quiz.not-found", "Quiz not found"), { status: 404 })
  )
];

export function emptyQuizPage(): QuizPageResponse {
  return { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
}

export function emptyQuestionPage(): QuestionPageResponse {
  return { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
}
