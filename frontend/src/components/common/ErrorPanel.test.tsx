import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ApiClientError } from "@/api/apiError";
import { ErrorPanel } from "@/components/common/ErrorPanel";

describe("ErrorPanel", () => {
  it("shows the correlation id when the error carries one", () => {
    const error = new ApiClientError("internal.error", "Something broke", 500, [], "corr-abc-123");

    render(<ErrorPanel error={error} />);

    expect(screen.getByText(/Reference: corr-abc-123/)).toBeInTheDocument();
  });

  it("omits the reference line when there is no correlation id", () => {
    const error = new ApiClientError("quiz.not-found", "Quiz not found", 404);

    render(<ErrorPanel error={error} />);

    expect(screen.queryByText(/Reference:/)).not.toBeInTheDocument();
  });

  it("omits the reference line for a plain, non-API error", () => {
    render(<ErrorPanel error={new Error("boom")} />);

    expect(screen.queryByText(/Reference:/)).not.toBeInTheDocument();
  });
});
