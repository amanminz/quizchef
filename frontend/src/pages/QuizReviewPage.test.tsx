import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { apiError, testIdentity } from "@/test/handlers";
import { questionResponse, quizResponse } from "@/test/quizFixtures";
import { server } from "@/test/server";
import { currentPath, renderApp } from "@/test/testUtils";

function signIn() {
  useAuthStore.setState({ token: testIdentity.token });
}

describe("QuizReviewPage", () => {
  it("warns before publishing a quiz with no questions", async () => {
    signIn();
    const quiz = quizResponse({ id: "quiz-1", state: "DRAFT", questionIds: [] });
    server.use(http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)));

    renderApp("/quizzes/quiz-1/review");

    expect(await screen.findByText(/add at least one question/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^publish$/i })).toBeDisabled();
  });

  it("summarizes difficulty and language coverage", async () => {
    signIn();
    const question = questionResponse({
      id: "q-1",
      difficulty: "HARD",
      localizations: [{ languageCode: "en", title: "Jonah", prompt: "...", optionTexts: [] }]
    });
    const quiz = quizResponse({ id: "quiz-1", state: "DRAFT", questionIds: ["q-1"] });
    server.use(
      http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)),
      http.get("/api/v1/questions/q-1", () => HttpResponse.json(question))
    );

    renderApp("/quizzes/quiz-1/review");

    expect(await screen.findByText(/HARD: 1/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^publish$/i })).toBeEnabled();
  });

  it("publishes after confirmation and returns to My Quizzes", async () => {
    signIn();
    const question = questionResponse({ id: "q-1" });
    const quiz = quizResponse({ id: "quiz-1", state: "DRAFT", questionIds: ["q-1"] });
    server.use(
      http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)),
      http.get("/api/v1/questions/q-1", () => HttpResponse.json(question)),
      http.get("/api/v1/quizzes/mine", () => HttpResponse.json({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })),
      http.post("/api/v1/quizzes/quiz-1/publish", () => HttpResponse.json({ ...quiz, state: "PUBLISHED" }))
    );
    const user = userEvent.setup();

    renderApp("/quizzes/quiz-1/review");
    await user.click(await screen.findByRole("button", { name: /^publish$/i }));
    const dialogPublish = (await screen.findAllByRole("button", { name: /^publish$/i })).at(-1)!;
    await user.click(dialogPublish);

    expect(await screen.findByRole("heading", { name: "Quizzes" })).toBeInTheDocument();
    expect(currentPath()).toBe("/quizzes");
  });

  it("shows the server's error when publishing fails", async () => {
    signIn();
    const question = questionResponse({ id: "q-1" });
    const quiz = quizResponse({ id: "quiz-1", state: "DRAFT", questionIds: ["q-1"] });
    server.use(
      http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)),
      http.get("/api/v1/questions/q-1", () => HttpResponse.json(question)),
      http.post("/api/v1/quizzes/quiz-1/publish", () =>
        HttpResponse.json(apiError("quiz.not-publishable", "Not ready to publish"), { status: 409 })
      )
    );
    const user = userEvent.setup();

    renderApp("/quizzes/quiz-1/review");
    await user.click(await screen.findByRole("button", { name: /^publish$/i }));
    const dialogPublish = (await screen.findAllByRole("button", { name: /^publish$/i })).at(-1)!;
    await user.click(dialogPublish);

    expect(await screen.findByText(/not ready to publish/i)).toBeInTheDocument();
    expect(currentPath()).toBe("/quizzes/quiz-1/review");
  });

  it("archives a published quiz after confirmation", async () => {
    signIn();
    const quiz = quizResponse({ id: "quiz-1", state: "PUBLISHED", questionIds: ["q-1"] });
    server.use(
      http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)),
      http.get("/api/v1/questions/q-1", () => HttpResponse.json(questionResponse({ id: "q-1" }))),
      http.get("/api/v1/quizzes/mine", () => HttpResponse.json({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })),
      http.post("/api/v1/quizzes/quiz-1/archive", () => HttpResponse.json({ ...quiz, state: "ARCHIVED" }))
    );
    const user = userEvent.setup();

    renderApp("/quizzes/quiz-1/review");
    await user.click(await screen.findByRole("button", { name: /^archive$/i }));
    const dialogArchive = (await screen.findAllByRole("button", { name: /^archive$/i })).at(-1)!;
    await user.click(dialogArchive);

    expect(await screen.findByRole("heading", { name: "Quizzes" })).toBeInTheDocument();
    expect(currentPath()).toBe("/quizzes");
  });
});
