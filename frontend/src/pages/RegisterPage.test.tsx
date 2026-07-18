import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { apiError, memberCurrentUser, testIdentity } from "@/test/handlers";
import { server } from "@/test/server";
import { currentPath, renderApp } from "@/test/testUtils";

describe("RegisterPage", () => {
  it("registers, logs straight in, and lands on the member dashboard", async () => {
    server.use(
      http.post("/api/v1/auth/register", () =>
        HttpResponse.json(
          {
            identityId: testIdentity.identityId,
            displayName: "Test Member",
            email: testIdentity.email,
            createdAt: new Date().toISOString()
          },
          { status: 201 }
        )
      ),
      // A fresh registration is a plain member.
      http.get("/api/v1/users/me", () => HttpResponse.json(memberCurrentUser()))
    );
    const user = userEvent.setup();

    renderApp("/register");
    await user.type(screen.getByLabelText(/display name/i), "Test Member");
    await user.type(screen.getByLabelText(/email/i), testIdentity.email);
    await user.type(screen.getByLabelText(/password/i), testIdentity.password);
    await user.click(screen.getByRole("button", { name: /create account/i }));

    expect(await screen.findByText("Test Member")).toBeInTheDocument();
    expect(currentPath()).toBe("/dashboard");
    expect(useAuthStore.getState().token).toBe(testIdentity.token);
    // A new member sees the path to hosting, not the hosting workflows.
    expect(screen.getByRole("heading", { name: /become a host/i })).toBeInTheDocument();
  });

  it("surfaces a duplicate email as the server states it", async () => {
    server.use(
      http.post("/api/v1/auth/register", () =>
        HttpResponse.json(apiError("identity.email.duplicate", "Email is already registered"), {
          status: 409
        })
      )
    );
    const user = userEvent.setup();

    renderApp("/register");
    await user.type(screen.getByLabelText(/display name/i), "Test Member");
    await user.type(screen.getByLabelText(/email/i), testIdentity.email);
    await user.type(screen.getByLabelText(/password/i), testIdentity.password);
    await user.click(screen.getByRole("button", { name: /create account/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/already registered/i);
    expect(currentPath()).toBe("/register");
    expect(useAuthStore.getState().token).toBeNull();
  });
});
