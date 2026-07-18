import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ApiClientError } from "@/api/apiError";
import { ErrorBoundary } from "@/components/feedback/ErrorBoundary";

function Boom(): never {
  throw new Error("render exploded");
}

function BoomWithCorrelation(): never {
  throw new ApiClientError("internal.error", "The server hit an unexpected error", 500, [],
    "corr-fatal-456");
}

describe("ErrorBoundary", () => {
  it("catches a render error and shows the error panel instead of a blank page", () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);

    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>
    );

    expect(screen.getByRole("alert")).toHaveTextContent("render exploded");
    expect(screen.getByRole("button", { name: /try again/i })).toBeInTheDocument();
    consoleError.mockRestore();
  });

  it("shows the correlation id in the fatal error dialog when the error carries one", () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);

    render(
      <ErrorBoundary>
        <BoomWithCorrelation />
      </ErrorBoundary>
    );

    expect(screen.getByRole("alert")).toHaveTextContent("corr-fatal-456");
    consoleError.mockRestore();
  });

  it("renders children normally when nothing throws", () => {
    render(
      <ErrorBoundary>
        <p>All good</p>
      </ErrorBoundary>
    );

    expect(screen.getByText("All good")).toBeInTheDocument();
  });
});
