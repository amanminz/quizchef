import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { isApiClientError } from "@/api/apiError";
import { apiClient } from "@/api/axios";
import { useAuthStore } from "@/auth/authStore";
import { apiError } from "@/test/handlers";
import { server } from "@/test/server";

describe("api client", () => {
  it("injects the bearer token from the auth store", async () => {
    useAuthStore.setState({ token: "jwt-123" });
    let seenAuthorization: string | null = null;
    server.use(
      http.get("/api/v1/probe", ({ request }) => {
        seenAuthorization = request.headers.get("Authorization");
        return HttpResponse.json({});
      })
    );

    await apiClient.get("/api/v1/probe");

    expect(seenAuthorization).toBe("Bearer jwt-123");
  });

  it("sends no Authorization header when unauthenticated", async () => {
    let seenAuthorization: string | null = "unset";
    server.use(
      http.get("/api/v1/probe", ({ request }) => {
        seenAuthorization = request.headers.get("Authorization");
        return HttpResponse.json({});
      })
    );

    await apiClient.get("/api/v1/probe");

    expect(seenAuthorization).toBeNull();
  });

  it("maps a backend ApiError body onto ApiClientError", async () => {
    server.use(
      http.post("/api/v1/probe", () =>
        HttpResponse.json(
          {
            ...apiError("quiz.content.locked", "Published quizzes cannot change content"),
            fieldErrors: [{ field: "title", message: "must not be blank" }]
          },
          { status: 409 }
        )
      )
    );

    const failure = await apiClient.post("/api/v1/probe").catch((error: unknown) => error);

    expect(isApiClientError(failure)).toBe(true);
    if (isApiClientError(failure)) {
      expect(failure.code).toBe("quiz.content.locked");
      expect(failure.status).toBe(409);
      expect(failure.fieldErrors).toEqual([{ field: "title", message: "must not be blank" }]);
    }
  });

  it("expires the local session on a 401 outside the auth endpoints", async () => {
    useAuthStore.setState({ token: "stale-token" });
    server.use(
      http.get("/api/v1/probe", () =>
        HttpResponse.json(apiError("identity.token.expired", "Token expired"), { status: 401 })
      )
    );

    await apiClient.get("/api/v1/probe").catch(() => undefined);

    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().sessionExpired).toBe(true);
  });

  it("does not treat a failed login attempt as an expired session", async () => {
    const failure = await apiClient
      .post("/api/v1/auth/login", { email: "host@example.com", password: "wrong" })
      .catch((error: unknown) => error);

    expect(isApiClientError(failure)).toBe(true);
    expect(useAuthStore.getState().sessionExpired).toBe(false);
  });

  it("maps a connection failure to a network error code", async () => {
    server.use(http.get("/api/v1/probe", () => HttpResponse.error()));

    const failure = await apiClient.get("/api/v1/probe").catch((error: unknown) => error);

    expect(isApiClientError(failure)).toBe(true);
    if (isApiClientError(failure)) {
      expect(failure.code).toBe("network.unavailable");
      expect(failure.status).toBeNull();
    }
  });
});
