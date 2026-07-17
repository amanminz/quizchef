import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { apiError, testIdentity } from "@/test/handlers";
import { quizPage, quizSummary } from "@/test/quizFixtures";
import { server } from "@/test/server";
import { renderApp } from "@/test/testUtils";

function signIn() {
  useAuthStore.setState({ token: testIdentity.token });
}

describe("QuizzesPage", () => {
  it("sections quizzes by lifecycle state", async () => {
    signIn();
    server.use(
      http.get("/api/v1/quizzes/mine", () =>
        HttpResponse.json(
          quizPage([
            quizSummary({ title: "My Draft", state: "DRAFT" }),
            quizSummary({ title: "My Published Quiz", state: "PUBLISHED" })
          ])
        )
      )
    );

    renderApp("/quizzes");

    expect(await screen.findByText("My Draft")).toBeInTheDocument();
    expect(screen.getByText("My Published Quiz")).toBeInTheDocument();
    expect(screen.getByText("Draft")).toBeInTheDocument();
    expect(screen.getByText("Published")).toBeInTheDocument();
    expect(screen.queryByText("Archived")).not.toBeInTheDocument();
  });

  it("shows an empty state with no quizzes", async () => {
    signIn();
    server.use(http.get("/api/v1/quizzes/mine", () => HttpResponse.json(quizPage([]))));

    renderApp("/quizzes");

    expect(await screen.findByText("No quizzes yet")).toBeInTheDocument();
  });

  it("shows an error panel when the list fails to load", async () => {
    signIn();
    server.use(
      http.get("/api/v1/quizzes/mine", () =>
        HttpResponse.json(apiError("internal.error", "Something broke"), { status: 500 })
      )
    );

    renderApp("/quizzes");

    expect(await screen.findByRole("alert")).toHaveTextContent(/something broke/i);
  });

  it("publishes a draft quiz from its card", async () => {
    signIn();
    // Stateful fixture: the list handler reflects whatever the publish
    // handler last wrote, matching how invalidate-then-refetch behaves
    // against a real backend.
    let quiz = quizSummary({ title: "Ready to publish", state: "DRAFT" });
    server.use(
      http.get("/api/v1/quizzes/mine", () => HttpResponse.json(quizPage([quiz]))),
      http.post(`/api/v1/quizzes/${quiz.id}/publish`, () => {
        quiz = { ...quiz, state: "PUBLISHED" };
        return HttpResponse.json({ id: quiz.id, state: "PUBLISHED", version: 1 });
      })
    );
    const user = userEvent.setup();

    renderApp("/quizzes");
    const card = (await screen.findByText("Ready to publish")).closest("div.rounded-lg")!;
    await user.click(within(card as HTMLElement).getByRole("button", { name: /publish/i }));

    await waitFor(() => {
      expect(within(card as HTMLElement).queryByRole("button", { name: /publish/i })).not.toBeInTheDocument();
    });
  });

  it("archives a published quiz from its card", async () => {
    signIn();
    let quiz = quizSummary({ title: "Live quiz", state: "PUBLISHED" });
    server.use(
      http.get("/api/v1/quizzes/mine", () => HttpResponse.json(quizPage([quiz]))),
      http.post(`/api/v1/quizzes/${quiz.id}/archive`, () => {
        quiz = { ...quiz, state: "ARCHIVED" };
        return HttpResponse.json({ id: quiz.id, state: "ARCHIVED", version: 1 });
      })
    );
    const user = userEvent.setup();

    renderApp("/quizzes");
    const card = (await screen.findByText("Live quiz")).closest("div.rounded-lg")!;
    await user.click(within(card as HTMLElement).getByRole("button", { name: /archive/i }));

    await waitFor(() => {
      expect(within(card as HTMLElement).queryByRole("button", { name: /archive/i })).not.toBeInTheDocument();
    });
  });
});
