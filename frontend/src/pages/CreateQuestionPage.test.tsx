import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { apiError, testIdentity } from "@/test/handlers";
import { questionPage, questionResponse, questionSummary, quizResponse } from "@/test/quizFixtures";
import { server } from "@/test/server";
import { currentPath, renderApp } from "@/test/testUtils";
import type { CreateQuestionRequest } from "@/types/api";

function signIn() {
  useAuthStore.setState({ token: testIdentity.token });
}

async function fillValidQuestion(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText("Title"), "Exodus leader");
  await user.type(screen.getByLabelText("Prompt"), "Who led Israel out of Egypt?");
  await user.type(screen.getByLabelText("Option 1 text"), "Moses");
  await user.type(screen.getByLabelText("Option 2 text"), "Aaron");
}

describe("CreateQuestionPage", () => {
  it("validates per question type before any request is made", async () => {
    signIn();
    let created = false;
    server.use(
      http.post("/api/v1/questions", () => {
        created = true;
        return HttpResponse.json(questionResponse());
      })
    );
    const user = userEvent.setup();

    renderApp("/questions/new");
    await screen.findByLabelText("Title");
    await user.click(screen.getByRole("button", { name: "Publish" }));

    expect(await screen.findByText("Title is required")).toBeInTheDocument();
    expect(screen.getAllByText("Option text is required").length).toBeGreaterThan(0);
    expect(created).toBe(false);
  });

  it("publishes and auto-attaches when authoring was launched from a quiz", async () => {
    signIn();
    let createBody: CreateQuestionRequest | undefined;
    let attachBody: { questionId?: string } | undefined;
    let quiz = quizResponse({ id: "quiz-1", questionIds: [] });
    server.use(
      http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)),
      http.get("/api/v1/questions", () =>
        HttpResponse.json(
          questionPage(attachBody ? [questionSummary({ id: "q-new", title: "Exodus leader" })] : [])
        )
      ),
      http.post("/api/v1/questions", async ({ request }) => {
        createBody = (await request.json()) as CreateQuestionRequest;
        return HttpResponse.json(questionResponse({ id: "q-new", state: "DRAFT" }), {
          status: 201
        });
      }),
      http.post("/api/v1/questions/q-new/publish", () =>
        HttpResponse.json(questionResponse({ id: "q-new", state: "PUBLISHED" }))
      ),
      http.post("/api/v1/quizzes/quiz-1/questions", async ({ request }) => {
        attachBody = (await request.json()) as { questionId?: string };
        quiz = { ...quiz, questionIds: ["q-new"] };
        return HttpResponse.json(quiz);
      })
    );
    const user = userEvent.setup();

    renderApp("/questions/new?quizId=quiz-1");
    await screen.findByLabelText("Title");
    await fillValidQuestion(user);
    await user.click(screen.getByRole("button", { name: "Publish" }));

    await waitFor(() => expect(currentPath()).toBe("/quizzes/quiz-1/questions"));
    expect(
      await screen.findByText("Question published and added to this quiz.")
    ).toBeInTheDocument();
    expect(attachBody?.questionId).toBe("q-new");
    expect(createBody?.questionType).toBe("SINGLE_CHOICE");
    expect(createBody?.options).toEqual([
      { text: "Moses", correct: true, displayOrder: 1 },
      { text: "Aaron", correct: false, displayOrder: 2 }
    ]);
    expect(await screen.findByText("Selected (1)")).toBeInTheDocument();
  });

  it("reports the partial success honestly when attaching fails after publish", async () => {
    signIn();
    const quiz = quizResponse({ id: "quiz-1", questionIds: [] });
    server.use(
      http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)),
      http.get("/api/v1/questions", () =>
        HttpResponse.json(questionPage([questionSummary({ id: "q-new", title: "Exodus leader" })]))
      ),
      http.post("/api/v1/questions", () =>
        HttpResponse.json(questionResponse({ id: "q-new", state: "DRAFT" }), { status: 201 })
      ),
      http.post("/api/v1/questions/q-new/publish", () =>
        HttpResponse.json(questionResponse({ id: "q-new", state: "PUBLISHED" }))
      ),
      http.post("/api/v1/quizzes/quiz-1/questions", () =>
        HttpResponse.json(apiError("quiz.not-found", "boom"), { status: 500 })
      )
    );
    const user = userEvent.setup();

    renderApp("/questions/new?quizId=quiz-1");
    await screen.findByLabelText("Title");
    await fillValidQuestion(user);
    await user.click(screen.getByRole("button", { name: "Publish" }));

    await waitFor(() => expect(currentPath()).toBe("/quizzes/quiz-1/questions"));
    expect(
      await screen.findByText(/Question published, but it could not be added to this quiz/)
    ).toBeInTheDocument();
    // The published question is visible in the library, not claimed as selected.
    expect(screen.getByText("Selected (0)")).toBeInTheDocument();
    expect(await screen.findByText("Exodus leader")).toBeInTheDocument();
  });

  it("saves a draft and continues editing it, keeping the quiz context", async () => {
    signIn();
    const draft = questionResponse({
      id: "q-new",
      state: "DRAFT",
      options: [
        { id: "opt-1", correct: true, displayOrder: 1 },
        { id: "opt-2", correct: false, displayOrder: 2 }
      ],
      localizations: [
        {
          languageCode: "en",
          title: "Exodus leader",
          prompt: "Who led Israel out of Egypt?",
          optionTexts: [
            { optionId: "opt-1", text: "Moses" },
            { optionId: "opt-2", text: "Aaron" }
          ]
        }
      ]
    });
    server.use(
      http.post("/api/v1/questions", () => HttpResponse.json(draft, { status: 201 })),
      http.get("/api/v1/questions/q-new", () => HttpResponse.json(draft))
    );
    const user = userEvent.setup();

    renderApp("/questions/new?quizId=quiz-1");
    await screen.findByLabelText("Title");
    await fillValidQuestion(user);
    await user.click(screen.getByRole("button", { name: "Save draft" }));

    await waitFor(() => expect(currentPath()).toBe("/questions/q-new/edit"));
    expect(await screen.findByText(/Draft saved/)).toBeInTheDocument();
    expect(screen.getByDisplayValue("Exodus leader")).toBeInTheDocument();
  });

  it("cancel returns to the quiz Questions step without mutating anything", async () => {
    signIn();
    let mutated = false;
    const quiz = quizResponse({ id: "quiz-1", questionIds: [] });
    server.use(
      http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)),
      http.post("/api/v1/questions", () => {
        mutated = true;
        return HttpResponse.json(questionResponse(), { status: 201 });
      })
    );
    const user = userEvent.setup();

    renderApp("/questions/new?quizId=quiz-1");
    await screen.findByLabelText("Title");
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    await waitFor(() => expect(currentPath()).toBe("/quizzes/quiz-1/questions"));
    expect(mutated).toBe(false);
  });
});
