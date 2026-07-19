import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { apiError, testIdentity } from "@/test/handlers";
import { questionResponse } from "@/test/quizFixtures";
import { server } from "@/test/server";
import { currentPath, renderApp } from "@/test/testUtils";

function signIn() {
  useAuthStore.setState({ token: testIdentity.token });
}

function storedQuestion(state: "DRAFT" | "PUBLISHED" | "ARCHIVED" = "DRAFT") {
  return questionResponse({
    id: "q-1",
    state,
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

function serveUsage(quizCount: number) {
  server.use(
    http.get("/api/v1/questions/q-1/usage", () =>
      HttpResponse.json({ questionId: "q-1", quizCount })
    )
  );
}

describe("QuestionDetailPage", () => {
  it("renders the question read-only at its id-keyed route", async () => {
    signIn();
    server.use(http.get("/api/v1/questions/q-1", () => HttpResponse.json(storedQuestion())));
    serveUsage(0);

    renderApp("/questions/q-1");

    expect(await screen.findByText("Who led Israel out of Egypt?")).toBeInTheDocument();
    expect(screen.getByText("Moses")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /edit/i })).toHaveAttribute(
      "href",
      "/questions/q-1/edit"
    );
  });

  it("restores an archived question in place — same id, no copy", async () => {
    signIn();
    let restored = false;
    server.use(
      http.get("/api/v1/questions/q-1", () =>
        HttpResponse.json(storedQuestion(restored ? "PUBLISHED" : "ARCHIVED"))
      ),
      http.post("/api/v1/questions/q-1/restore", () => {
        restored = true;
        return HttpResponse.json(storedQuestion("PUBLISHED"));
      })
    );
    serveUsage(1);
    const user = userEvent.setup();

    renderApp("/questions/q-1");

    await user.click(await screen.findByRole("button", { name: /restore/i }));

    await waitFor(() => expect(restored).toBe(true));
    expect(await screen.findByText("PUBLISHED")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /restore/i })).not.toBeInTheDocument();
  });

  it("blocks deleting a question that quizzes still use, naming the count", async () => {
    signIn();
    server.use(http.get("/api/v1/questions/q-1", () => HttpResponse.json(storedQuestion())));
    serveUsage(2);

    renderApp("/questions/q-1");

    expect(
      await screen.findByText(/used in 2 quizzes\. Remove it from those quizzes before deleting\./i)
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /delete question/i })).toBeDisabled();
  });

  it("deletes an unused question after a confirmation naming its title", async () => {
    signIn();
    let deleted = false;
    server.use(
      http.get("/api/v1/questions/q-1", () => HttpResponse.json(storedQuestion())),
      http.delete("/api/v1/questions/q-1", () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      })
    );
    serveUsage(0);
    const user = userEvent.setup();

    renderApp("/questions/q-1");

    await user.click(await screen.findByRole("button", { name: /delete question/i }));
    expect(await screen.findByText('Delete "Exodus leader"?')).toBeInTheDocument();
    await user.click(screen.getAllByRole("button", { name: /delete question/i }).at(-1)!);

    await waitFor(() => expect(deleted).toBe(true));
    await waitFor(() => expect(currentPath()).toBe("/questions"));
  });

  it("surfaces the server's in-use rejection even when the UI allowed the attempt", async () => {
    signIn();
    server.use(
      http.get("/api/v1/questions/q-1", () => HttpResponse.json(storedQuestion())),
      http.delete("/api/v1/questions/q-1", () =>
        HttpResponse.json(
          apiError(
            "question.in-use",
            "This question is used in 1 quiz. Remove it from those quizzes before deleting."
          ),
          { status: 409 }
        )
      )
    );
    serveUsage(0);
    const user = userEvent.setup();

    renderApp("/questions/q-1");

    await user.click(await screen.findByRole("button", { name: /delete question/i }));
    await user.click(screen.getAllByRole("button", { name: /delete question/i }).at(-1)!);

    expect(await screen.findByText(/used in 1 quiz/i)).toBeInTheDocument();
    expect(currentPath()).toBe("/questions/q-1");
  });
});
