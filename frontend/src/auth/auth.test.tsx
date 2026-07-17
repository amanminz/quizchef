import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { testIdentity } from "@/test/handlers";
import { currentPath, renderApp } from "@/test/testUtils";

async function signIn(email: string, password: string) {
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText("Email"), email);
  await user.type(screen.getByLabelText("Password"), password);
  await user.click(screen.getByRole("button", { name: /sign in/i }));
  return user;
}

describe("authentication", () => {
  it("logs in, stores the token, and lands on the dashboard with the current user", async () => {
    renderApp("/login");

    await signIn(testIdentity.email, testIdentity.password);

    expect(await screen.findByText(/signed in/i)).toBeInTheDocument();
    expect(currentPath()).toBe("/dashboard");
    expect(useAuthStore.getState().token).toBe(testIdentity.token);
    expect(screen.getAllByText(/QUIZ_MASTER/).length).toBeGreaterThan(0);
  });

  it("shows the backend's error message for a rejected login and stores nothing", async () => {
    renderApp("/login");

    await signIn(testIdentity.email, "WrongPassword123");

    expect(await screen.findByRole("alert")).toHaveTextContent(/invalid email or password/i);
    expect(useAuthStore.getState().token).toBeNull();
  });

  it("returns the visitor to the page they were headed for after login", async () => {
    renderApp("/quizzes");
    expect(await screen.findByRole("heading", { name: /sign in/i })).toBeInTheDocument();

    await signIn(testIdentity.email, testIdentity.password);

    expect(await screen.findByRole("heading", { name: "Quizzes" })).toBeInTheDocument();
    expect(currentPath()).toBe("/quizzes");
  });

  it("treats a rejected token as an expired session: clears auth and returns to login", async () => {
    useAuthStore.setState({ token: "a-revoked-token" });
    renderApp("/dashboard");

    expect(await screen.findByText(/session has expired/i)).toBeInTheDocument();
    expect(currentPath()).toBe("/login");
    expect(useAuthStore.getState().token).toBeNull();
  });

  it("logs out from the dashboard back to the public area", async () => {
    useAuthStore.setState({ token: testIdentity.token });
    renderApp("/dashboard");
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: /log out/i }));

    expect(await screen.findByRole("heading", { name: /sign in/i })).toBeInTheDocument();
    expect(currentPath()).toBe("/login");
    expect(useAuthStore.getState().token).toBeNull();
  });
});
