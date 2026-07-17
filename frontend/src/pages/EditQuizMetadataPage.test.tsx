import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { apiError, testIdentity } from "@/test/handlers";
import { quizResponse } from "@/test/quizFixtures";
import { server } from "@/test/server";
import { renderApp } from "@/test/testUtils";

function signIn() {
  useAuthStore.setState({ token: testIdentity.token });
}

describe("EditQuizMetadataPage", () => {
  it("loads and displays the quiz's existing metadata", async () => {
    signIn();
    const quiz = quizResponse({
      id: "quiz-1",
      localizations: [{ languageCode: "en", title: "Exodus Quiz", description: "Chapter 1-5" }]
    });
    server.use(http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)));

    renderApp("/quizzes/quiz-1");

    expect(await screen.findByDisplayValue("Exodus Quiz")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Chapter 1-5")).toBeInTheDocument();
  });

  it("saves an edited title, preserving other translations", async () => {
    signIn();
    const quiz = quizResponse({
      id: "quiz-1",
      version: 0,
      localizations: [
        { languageCode: "en", title: "Exodus Quiz", description: "Chapter 1-5" },
        { languageCode: "kn", title: "ವಿಮೋಚನಕಾಂಡ", description: undefined }
      ]
    });
    let putBody: unknown;
    server.use(
      http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)),
      http.put("/api/v1/quizzes/quiz-1", async ({ request }) => {
        putBody = await request.json();
        return HttpResponse.json({ ...quiz, version: 1 });
      })
    );
    const user = userEvent.setup();

    renderApp("/quizzes/quiz-1");
    const titleInput = await screen.findByDisplayValue("Exodus Quiz");
    await user.clear(titleInput);
    await user.type(titleInput, "Exodus Quiz Revised");
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    await waitFor(() => expect(putBody).toBeDefined());
    const body = putBody as { version: number; localizations: { languageCode: string; title: string }[] };
    expect(body.version).toBe(0);
    expect(body.localizations).toHaveLength(2);
    expect(body.localizations.find((l) => l.languageCode === "en")?.title).toBe("Exodus Quiz Revised");
    expect(body.localizations.find((l) => l.languageCode === "kn")?.title).toBe("ವಿಮೋಚನಕಾಂಡ");
  });

  it("disables content fields once the quiz is published", async () => {
    signIn();
    const quiz = quizResponse({ id: "quiz-1", state: "PUBLISHED" });
    server.use(http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)));

    renderApp("/quizzes/quiz-1");

    expect(await screen.findByLabelText("Title")).toBeDisabled();
    expect(screen.getByLabelText("Visibility")).toBeEnabled();
  });

  it("shows a conflict error on a stale version", async () => {
    signIn();
    const quiz = quizResponse({ id: "quiz-1", version: 0 });
    server.use(
      http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)),
      http.put("/api/v1/quizzes/quiz-1", () =>
        HttpResponse.json(apiError("quiz.concurrent-modification", "Someone else saved first"), {
          status: 409
        })
      )
    );
    const user = userEvent.setup();

    renderApp("/quizzes/quiz-1");
    await screen.findByDisplayValue("Bible Quiz");
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    expect(await screen.findByText(/someone else saved first/i)).toBeInTheDocument();
  });
});
