import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { memberCurrentUser, testIdentity } from "@/test/handlers";
import { questionPage, questionSummary, quizResponse } from "@/test/quizFixtures";
import { server } from "@/test/server";
import { renderApp } from "@/test/testUtils";

function signIn() {
  useAuthStore.setState({ token: testIdentity.token });
}

describe("QuizQuestionsPage", () => {
  it("shows the current composition and the addable library separately", async () => {
    signIn();
    const attached = questionSummary({ id: "q-attached", title: "Jonah" });
    const addable = questionSummary({ id: "q-addable", title: "Moses" });
    const quiz = quizResponse({ id: "quiz-1", questionIds: [attached.id!] });
    server.use(
      http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)),
      http.get("/api/v1/questions", () => HttpResponse.json(questionPage([attached, addable])))
    );

    renderApp("/quizzes/quiz-1/questions");

    expect(await screen.findByText("Selected (1)")).toBeInTheDocument();
    const selectedSection = screen.getByText("Selected (1)").closest("section")!;
    expect(within(selectedSection).getByText("Jonah")).toBeInTheDocument();
    expect(within(selectedSection).queryByText("Moses")).not.toBeInTheDocument();

    const librarySection = screen.getByText("Library").closest("section")!;
    expect(within(librarySection).getByText("Moses")).toBeInTheDocument();
    expect(within(librarySection).queryByText("Jonah")).not.toBeInTheDocument();
  });

  it("attaches a question from the library", async () => {
    signIn();
    const addable = questionSummary({ id: "q-addable", title: "Moses" });
    let quiz = quizResponse({ id: "quiz-1", questionIds: [] });
    server.use(
      http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)),
      http.get("/api/v1/questions", () => HttpResponse.json(questionPage([addable]))),
      http.post("/api/v1/quizzes/quiz-1/questions", async ({ request }) => {
        const body = (await request.json()) as { questionId: string };
        quiz = { ...quiz, questionIds: [...(quiz.questionIds ?? []), body.questionId] };
        return HttpResponse.json(quiz);
      })
    );
    const user = userEvent.setup();

    renderApp("/quizzes/quiz-1/questions");
    await screen.findByText("Moses");
    await user.click(screen.getByRole("button", { name: /^add$/i }));

    await waitFor(() => expect(screen.getByText("Selected (1)")).toBeInTheDocument());
  });

  it("detaches a question from the composition", async () => {
    signIn();
    const attached = questionSummary({ id: "q-attached", title: "Jonah" });
    let quiz = quizResponse({ id: "quiz-1", questionIds: [attached.id!] });
    server.use(
      http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)),
      http.get("/api/v1/questions", () => HttpResponse.json(questionPage([attached]))),
      http.delete("/api/v1/quizzes/quiz-1/questions/q-attached", () => {
        quiz = { ...quiz, questionIds: [] };
        return HttpResponse.json(quiz);
      })
    );
    const user = userEvent.setup();

    renderApp("/quizzes/quiz-1/questions");
    await screen.findByText("Selected (1)");
    await user.click(screen.getByRole("button", { name: /^remove$/i }));

    await waitFor(() => expect(screen.getByText("Selected (0)")).toBeInTheDocument());
  });

  it("gives an empty library an actionable path and distinct panel copy", async () => {
    signIn();
    const quiz = quizResponse({ id: "quiz-1", questionIds: [] });
    server.use(http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)));

    renderApp("/quizzes/quiz-1/questions");

    // The two panels' empty states say different things.
    expect(await screen.findByText("No questions selected")).toBeInTheDocument();
    expect(screen.getByText("Your question library is empty")).toBeInTheDocument();
    // The dead end is gone: both entry points into authoring exist.
    expect(screen.getByRole("button", { name: "Create Question" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "New Question" })).toBeInTheDocument();
    // Progression is guarded, with visible guidance rather than a bare disabled button.
    expect(screen.getByRole("button", { name: "Next: Review" })).toBeDisabled();
    expect(screen.getByText("Add at least one question to continue.")).toBeInTheDocument();
  });

  it("allows progression to review once a question is selected", async () => {
    signIn();
    const attached = questionSummary({ id: "q-attached", title: "Jonah" });
    const quiz = quizResponse({ id: "quiz-1", questionIds: [attached.id!] });
    server.use(
      http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)),
      http.get("/api/v1/questions", () => HttpResponse.json(questionPage([attached])))
    );

    renderApp("/quizzes/quiz-1/questions");

    await screen.findByText("Selected (1)");
    expect(screen.getByRole("button", { name: "Next: Review" })).toBeEnabled();
    expect(screen.queryByText("Add at least one question to continue.")).not.toBeInTheDocument();
  });

  it("keeps drafts attachable (the domain's policy) with their status and an edit path", async () => {
    signIn();
    const draft = questionSummary({ id: "q-draft", title: "Draft Q", state: "DRAFT" });
    const archived = questionSummary({ id: "q-archived", title: "Archived Q", state: "ARCHIVED" });
    const quiz = quizResponse({ id: "quiz-1", questionIds: [] });
    server.use(
      http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)),
      http.get("/api/v1/questions", () => HttpResponse.json(questionPage([draft, archived])))
    );

    renderApp("/quizzes/quiz-1/questions");

    await screen.findByText("Draft Q");
    // Drafts compose while still being refined; only archived is retired.
    const addButtons = screen.getAllByRole("button", { name: /^add$/i });
    expect(addButtons[0]).toBeEnabled();
    expect(addButtons[1]).toBeDisabled();
    expect(screen.getByText("Archived — no longer attachable")).toBeInTheDocument();
    // The row carries a DRAFT badge alongside the quiz header's own badge.
    const librarySection = screen.getByText("Library").closest("section")!;
    expect(within(librarySection).getByText("DRAFT")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Edit draft" })).toHaveAttribute(
      "href",
      "/questions/q-draft/edit?quizId=quiz-1"
    );
  });

  it("hides authoring controls from users without QUIZ_CREATE", async () => {
    signIn();
    const quiz = quizResponse({ id: "quiz-1", questionIds: [] });
    server.use(
      http.get("/api/v1/users/me", () => HttpResponse.json(memberCurrentUser())),
      http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz))
    );

    renderApp("/quizzes/quiz-1/questions");

    await screen.findByText("No questions selected");
    expect(screen.queryByRole("button", { name: "New Question" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Create Question" })).not.toBeInTheDocument();
  });

  it("filters the library by search text", async () => {
    signIn();
    const quiz = quizResponse({ id: "quiz-1", questionIds: [] });
    server.use(
      http.get("/api/v1/quizzes/quiz-1", () => HttpResponse.json(quiz)),
      http.get("/api/v1/questions", ({ request }) => {
        const search = new URL(request.url).searchParams.get("search");
        const all = [questionSummary({ title: "Jonah" }), questionSummary({ title: "Moses" })];
        const filtered = search
          ? all.filter((q) => q.title?.toLowerCase().includes(search.toLowerCase()))
          : all;
        return HttpResponse.json(questionPage(filtered));
      })
    );
    const user = userEvent.setup();

    renderApp("/quizzes/quiz-1/questions");
    await screen.findByText("Jonah");
    await user.type(screen.getByLabelText("Search questions"), "moses");

    await waitFor(() => expect(screen.queryByText("Jonah")).not.toBeInTheDocument());
    expect(screen.getByText("Moses")).toBeInTheDocument();
  });
});
