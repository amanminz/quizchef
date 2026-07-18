import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { apiError, memberCurrentUser, testIdentity } from "@/test/handlers";
import { server } from "@/test/server";
import { currentPath, renderApp } from "@/test/testUtils";
import type { CurrentUserResponse } from "@/types/api";

function signIn() {
  useAuthStore.setState({ token: testIdentity.token });
}

/** Serves /users/me from a mutable holder, so a promotion can flip it mid-test. */
function serveCurrentUser(holder: { user: CurrentUserResponse }) {
  server.use(http.get("/api/v1/users/me", () => HttpResponse.json(holder.user)));
}

describe("host onboarding", () => {
  it("adapts the dashboard and navigation to a plain member", async () => {
    signIn();
    serveCurrentUser({ user: memberCurrentUser() });

    renderApp("/dashboard");

    expect(await screen.findByText("Test Member")).toBeInTheDocument();
    // The path to hosting, not the hosting workflows.
    expect(screen.getByRole("heading", { name: /become a host/i })).toBeInTheDocument();
    expect(screen.queryByText(/host a session/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/author quizzes/i)).not.toBeInTheDocument();
    // Navigation reflects permissions: no authoring/hosting links, Profile present.
    const nav = screen.getByRole("navigation", { name: /primary/i });
    expect(within(nav).queryByRole("link", { name: "Quizzes" })).not.toBeInTheDocument();
    expect(within(nav).queryByRole("link", { name: "Sessions" })).not.toBeInTheDocument();
    expect(within(nav).getByRole("link", { name: "Profile" })).toBeInTheDocument();
  });

  it("shows the member's profile with roles and the path to hosting", async () => {
    signIn();
    serveCurrentUser({ user: memberCurrentUser() });

    renderApp("/profile");

    expect(await screen.findByText("Test Member")).toBeInTheDocument();
    expect(screen.getByText(testIdentity.email)).toBeInTheDocument();
    expect(screen.getByText("Member")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /become a host/i })).toBeInTheDocument();
  });

  it("promotes a member to host and every role-aware surface follows", async () => {
    signIn();
    const holder = { user: memberCurrentUser() };
    serveCurrentUser(holder);
    server.use(
      http.post("/api/v1/users/me/host-access", () => {
        // The grant is durable server-side; the next /users/me reflects it.
        holder.user = {
          ...holder.user,
          roles: ["USER", "QUIZ_MASTER"],
          permissions: [...(holder.user.permissions ?? []), "QUIZ_CREATE", "QUIZ_EDIT", "QUIZ_HOST"]
        };
        return HttpResponse.json({
          status: "GRANTED",
          roles: holder.user.roles,
          permissions: holder.user.permissions
        });
      })
    );
    const user = userEvent.setup();

    renderApp("/profile/host-access");
    await user.click(await screen.findByRole("button", { name: /become a host/i }));

    // Server-confirmed success, and the shared query refetch flips the nav.
    expect(await screen.findByText(/you're a host/i)).toBeInTheDocument();
    const nav = screen.getByRole("navigation", { name: /primary/i });
    await waitFor(() => {
      expect(within(nav).getByRole("link", { name: "Quizzes" })).toBeInTheDocument();
      expect(within(nav).getByRole("link", { name: "Sessions" })).toBeInTheDocument();
    });
    expect(currentPath()).toBe("/profile/host-access");
  });

  it("shows an already-host visitor the success state, not the request button", async () => {
    signIn();

    // Default handler: the test identity is already a host.
    renderApp("/profile/host-access");

    expect(await screen.findByText(/you're a host/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /become a host/i })).not.toBeInTheDocument();
  });

  it("surfaces a denied promotion and stays on the page", async () => {
    signIn();
    serveCurrentUser({ user: memberCurrentUser() });
    server.use(
      http.post("/api/v1/users/me/host-access", () =>
        HttpResponse.json(apiError("auth.permission.denied", "Permission is not granted"), {
          status: 403
        })
      )
    );
    const user = userEvent.setup();

    renderApp("/profile/host-access");
    await user.click(await screen.findByRole("button", { name: /become a host/i }));

    expect(await screen.findByText(/could not grant host access/i)).toBeInTheDocument();
    expect(currentPath()).toBe("/profile/host-access");
  });
});
