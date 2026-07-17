import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { apiError, testIdentity } from "@/test/handlers";
import { quizResponse } from "@/test/quizFixtures";
import { server } from "@/test/server";
import { currentPath, renderApp } from "@/test/testUtils";

function signIn() {
  useAuthStore.setState({ token: testIdentity.token });
}

describe("CreateQuizPage", () => {
  it("requires a title before submitting", async () => {
    signIn();
    const user = userEvent.setup();

    renderApp("/quizzes/new");
    await user.click(await screen.findByRole("button", { name: /create quiz/i }));

    expect(await screen.findByText(/title is required/i)).toBeInTheDocument();
  });

  it("creates a quiz and navigates straight into editing it", async () => {
    signIn();
    const created = quizResponse({
      id: "new-quiz-id",
      localizations: [{ languageCode: "en", title: "Genesis Quiz", description: undefined }]
    });
    server.use(
      http.post("/api/v1/quizzes", async ({ request }) => {
        const body = (await request.json()) as { localization?: { title?: string } };
        expect(body.localization?.title).toBe("Genesis Quiz");
        return HttpResponse.json(created, { status: 201 });
      }),
      http.get(`/api/v1/quizzes/${created.id}`, () => HttpResponse.json(created))
    );
    const user = userEvent.setup();

    renderApp("/quizzes/new");
    await user.type(await screen.findByLabelText("Title"), "Genesis Quiz");
    await user.click(screen.getByRole("button", { name: /create quiz/i }));

    await screen.findByRole("heading", { name: /genesis quiz/i });
    expect(currentPath()).toBe(`/quizzes/${created.id}`);
  });

  it("shows the backend's error message when creation fails", async () => {
    signIn();
    server.use(
      http.post("/api/v1/quizzes", () =>
        HttpResponse.json(apiError("request.invalid", "Unknown language tag"), { status: 400 })
      )
    );
    const user = userEvent.setup();

    renderApp("/quizzes/new");
    await user.type(await screen.findByLabelText("Title"), "Genesis Quiz");
    await user.click(screen.getByRole("button", { name: /create quiz/i }));

    expect(await screen.findByText(/unknown language tag/i)).toBeInTheDocument();
    expect(currentPath()).toBe("/quizzes/new");
  });
});
