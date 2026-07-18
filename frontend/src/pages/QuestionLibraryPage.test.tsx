import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { memberCurrentUser, testIdentity } from "@/test/handlers";
import { questionPage, questionResponse, questionSummary } from "@/test/quizFixtures";
import { server } from "@/test/server";
import { renderApp } from "@/test/testUtils";

function signIn() {
  useAuthStore.setState({ token: testIdentity.token });
}

describe("QuestionLibraryPage", () => {
  it("offers Create Question from the truly empty library", async () => {
    signIn();

    renderApp("/questions");

    expect(await screen.findByText("Your question library is empty")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Create Question" })).toBeInTheDocument();
  });

  it("distinguishes a filtered miss from an empty library and clears filters", async () => {
    signIn();
    const question = questionSummary({ title: "Moses" });
    server.use(
      http.get("/api/v1/questions", ({ request }) => {
        const search = new URL(request.url).searchParams.get("search");
        return HttpResponse.json(questionPage(search ? [] : [question]));
      })
    );
    const user = userEvent.setup();

    renderApp("/questions");
    await screen.findByText("Moses");
    await user.type(screen.getByLabelText("Search questions"), "jonah");

    expect(await screen.findByText("No questions match your filters")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Clear filters" }));
    expect(await screen.findByText("Moses")).toBeInTheDocument();
  });

  it("shows lifecycle actions per state: drafts edit/publish, published archive", async () => {
    signIn();
    const draft = questionSummary({ id: "q-draft", title: "Draft Q", state: "DRAFT" });
    const published = questionSummary({ id: "q-pub", title: "Published Q", state: "PUBLISHED" });
    server.use(
      http.get("/api/v1/questions", () => HttpResponse.json(questionPage([draft, published])))
    );

    renderApp("/questions");

    await screen.findByText("Draft Q");
    expect(screen.getByRole("button", { name: "Edit" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Publish" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Archive" })).toBeInTheDocument();
  });

  it("publishes a draft from the library", async () => {
    signIn();
    let published = false;
    server.use(
      http.get("/api/v1/questions", () =>
        HttpResponse.json(
          questionPage([
            questionSummary({
              id: "q-draft",
              title: "Draft Q",
              state: published ? "PUBLISHED" : "DRAFT"
            })
          ])
        )
      ),
      http.post("/api/v1/questions/q-draft/publish", () => {
        published = true;
        return HttpResponse.json(questionResponse({ id: "q-draft", state: "PUBLISHED" }));
      })
    );
    const user = userEvent.setup();

    renderApp("/questions");
    await screen.findByText("Draft Q");
    await user.click(screen.getByRole("button", { name: "Publish" }));

    await waitFor(() => expect(published).toBe(true));
    expect(await screen.findByRole("button", { name: "Archive" })).toBeInTheDocument();
  });

  it("keeps authoring navigation away from plain members", async () => {
    signIn();
    server.use(http.get("/api/v1/users/me", () => HttpResponse.json(memberCurrentUser())));

    renderApp("/dashboard");

    await screen.findByRole("link", { name: "Dashboard" });
    expect(screen.queryByRole("link", { name: "Question Library" })).not.toBeInTheDocument();
  });

  it("shows Question Library navigation to hosts", async () => {
    signIn();

    renderApp("/dashboard");

    expect(await screen.findByRole("link", { name: "Question Library" })).toBeInTheDocument();
  });
});
