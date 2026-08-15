import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { RemoveQuestionDialog } from "@/features/sessions/components/RemoveQuestionDialog";

function renderDialog(overrides: Partial<Parameters<typeof RemoveQuestionDialog>[0]> = {}) {
  return render(
    <RemoveQuestionDialog
      open
      onClose={vi.fn()}
      questionNumber={3}
      isCurrent
      answeredCount={0}
      isLastRemaining={false}
      onConfirmRemove={vi.fn().mockResolvedValue(undefined)}
      onCorrectInstead={vi.fn()}
      isRemoving={false}
      error={null}
      {...overrides}
    />
  );
}

describe("RemoveQuestionDialog", () => {
  it("confirms plainly when nobody has answered yet", () => {
    renderDialog();

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    // No alternative is offered because nothing is being thrown away.
    expect(screen.queryByRole("button", { name: /correct & replay/i })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /remove from session/i })).toBeInTheDocument();
  });

  it("warns and offers the alternative once participants have answered", () => {
    renderDialog({ answeredCount: 4 });

    const warning = screen.getByRole("alert");
    expect(warning).toHaveTextContent("4 participants have already answered");
    expect(warning).toHaveTextContent(/All answers and points from this question will be cancelled/i);
    // Correcting keeps the question in the quiz — usually what the host
    // actually wants, so it is offered right beside the destructive path.
    expect(screen.getByRole("button", { name: /correct & replay/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /remove & continue/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^cancel$/i })).toBeInTheDocument();
  });

  it("says the quiz will end when nothing follows the question", () => {
    renderDialog({ isLastRemaining: true, answeredCount: 2 });

    expect(screen.getByText(/removing it finishes the quiz/i)).toBeInTheDocument();
  });

  it("never claims answers exist for a question that is not in play", () => {
    renderDialog({ isCurrent: false, answeredCount: 0, questionNumber: 5 });

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByText(/Question 5 will be dropped from this session/i)).toBeInTheDocument();
  });
});
