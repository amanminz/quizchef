import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { currentPath, renderApp } from "@/test/testUtils";

describe("routing", () => {
  it("renders the home page at /", async () => {
    renderApp("/");
    expect(await screen.findByRole("heading", { name: "QuizChef" })).toBeInTheDocument();
    expect(screen.getByText(/join a game/i)).toBeInTheDocument();
  });

  it("renders the not-found page for unknown routes", async () => {
    renderApp("/definitely-not-a-route");
    expect(await screen.findByText("404")).toBeInTheDocument();
  });

  it("redirects unauthenticated visitors from the dashboard to login", async () => {
    renderApp("/dashboard");
    expect(await screen.findByRole("heading", { name: /sign in/i })).toBeInTheDocument();
    expect(currentPath()).toBe("/login");
  });

  it("keeps public play reachable without authentication", async () => {
    renderApp("/play");
    expect(await screen.findByRole("heading", { name: /join a game/i })).toBeInTheDocument();
  });

  it("keeps a public play-by-pin route reachable without authentication", async () => {
    renderApp("/play/042317");
    expect(await screen.findByLabelText(/your name/i)).toBeInTheDocument();
  });

  it.each([
    "/profile",
    "/profile/host-access",
    "/quizzes",
    "/quizzes/new",
    "/quizzes/some-id",
    "/quizzes/some-id/questions",
    "/quizzes/some-id/review",
    "/sessions",
    "/sessions/new",
    "/sessions/some-id",
    "/sessions/some-id/lobby",
    "/sessions/some-id/play"
  ])("redirects unauthenticated visitors from %s to login", async (route) => {
    renderApp(route);
    expect(await screen.findByRole("heading", { name: /sign in/i })).toBeInTheDocument();
    expect(currentPath()).toBe("/login");
  });
});
