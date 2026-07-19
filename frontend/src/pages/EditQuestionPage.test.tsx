import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { testIdentity } from "@/test/handlers";
import { questionResponse } from "@/test/quizFixtures";
import { server } from "@/test/server";
import { renderApp } from "@/test/testUtils";
import type { UpdateQuestionRequest } from "@/types/api";

function signIn() {
  useAuthStore.setState({ token: testIdentity.token });
}

function draftQuestion() {
  return questionResponse({
    id: "q-1",
    state: "DRAFT",
    questionType: "SINGLE_CHOICE",
    version: 0,
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
}

describe("EditQuestionPage", () => {
  it("updates a draft as a full PUT, keeping option ids and the read version", async () => {
    signIn();
    let updateBody: UpdateQuestionRequest | undefined;
    server.use(
      http.get("/api/v1/questions/q-1", () => HttpResponse.json(draftQuestion())),
      http.put("/api/v1/questions/q-1", async ({ request }) => {
        updateBody = (await request.json()) as UpdateQuestionRequest;
        return HttpResponse.json({ ...draftQuestion(), version: 1 });
      })
    );
    const user = userEvent.setup();

    renderApp("/questions/q-1/edit");
    const title = await screen.findByLabelText("Title");
    expect(title).toHaveValue("Exodus leader");
    await user.clear(title);
    await user.type(title, "Who led the Exodus?");
    await user.click(screen.getByRole("button", { name: "Save draft" }));

    expect(await screen.findByText("Draft saved.")).toBeInTheDocument();
    expect(updateBody?.version).toBe(0);
    expect(updateBody?.options.map((option) => option.id)).toEqual(["opt-1", "opt-2"]);
    const defaultLocalization = updateBody?.localizations.find(
      (localization) => localization.languageCode === "en"
    );
    expect(defaultLocalization?.title).toBe("Who led the Exodus?");
    expect(defaultLocalization?.optionTexts).toEqual([
      { optionId: "opt-1", text: "Moses" },
      { optionId: "opt-2", text: "Aaron" }
    ]);
  });

  it("breadcrumbs to the question's detail route by real id, never by title", async () => {
    signIn();
    server.use(http.get("/api/v1/questions/q-1", () => HttpResponse.json(draftQuestion())));

    renderApp("/questions/q-1/edit");

    const breadcrumb = await screen.findByRole("navigation", { name: /breadcrumb/i });
    const questionCrumb = await within(breadcrumb).findByRole("link", {
      name: "Exodus leader"
    });
    expect(questionCrumb).toHaveAttribute("href", "/questions/q-1");
    expect(
      within(breadcrumb).getByRole("link", { name: "Question Library" })
    ).toHaveAttribute("href", "/questions");
    // "Edit" is the current page — present, but never a link.
    expect(within(breadcrumb).getByText("Edit")).toBeInTheDocument();
    expect(
      within(breadcrumb).queryByRole("link", { name: "Edit" })
    ).not.toBeInTheDocument();
  });

  it("lets a draft change its question type and default language", async () => {
    signIn();
    server.use(http.get("/api/v1/questions/q-1", () => HttpResponse.json(draftQuestion())));

    renderApp("/questions/q-1/edit");

    expect(await screen.findByLabelText("Question type")).toBeEnabled();
    expect(screen.getByLabelText("Default language")).toBeEnabled();
  });

  it("refuses to edit a published question and explains why", async () => {
    signIn();
    server.use(
      http.get("/api/v1/questions/q-1", () =>
        HttpResponse.json({ ...draftQuestion(), state: "PUBLISHED" })
      )
    );

    renderApp("/questions/q-1/edit");

    expect(await screen.findByText(/Published questions are immutable/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Save draft" })).not.toBeInTheDocument();
  });

  it("publishes an edited draft and returns to the quiz with the question attached", async () => {
    signIn();
    let attached = false;
    server.use(
      http.get("/api/v1/questions/q-1", () => HttpResponse.json(draftQuestion())),
      http.put("/api/v1/questions/q-1", () =>
        HttpResponse.json({ ...draftQuestion(), version: 1 })
      ),
      http.post("/api/v1/questions/q-1/publish", () =>
        HttpResponse.json({ ...draftQuestion(), state: "PUBLISHED", version: 1 })
      ),
      http.get("/api/v1/quizzes/quiz-1", () =>
        HttpResponse.json({ id: "quiz-1", state: "DRAFT", questionIds: attached ? ["q-1"] : [] })
      ),
      http.post("/api/v1/quizzes/quiz-1/questions", () => {
        attached = true;
        return HttpResponse.json({ id: "quiz-1", questionIds: ["q-1"] });
      })
    );
    const user = userEvent.setup();

    renderApp("/questions/q-1/edit?quizId=quiz-1");
    await screen.findByLabelText("Title");
    await user.click(screen.getByRole("button", { name: "Publish" }));

    await waitFor(() => expect(attached).toBe(true));
    expect(
      await screen.findByText("Question published and added to this quiz.")
    ).toBeInTheDocument();
  });
});
