import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PresentationMetric } from "@/features/gameplay/components/PresentationMetric";

describe("PresentationMetric", () => {
  it("renders the label and value with a fixed minimum width and tabular numerals", () => {
    render(<PresentationMetric label="Answered" value="7 / 10" />);

    expect(screen.getByText("Answered")).toBeInTheDocument();
    const value = screen.getByText("7 / 10");
    expect(value.className).toContain("tabular-nums");
    expect(screen.getByRole("status").className).toContain("min-w-[6.5rem]");
  });

  it("gives two different metrics the exact same numeral font size", () => {
    const { unmount } = render(<PresentationMetric label="Answered" value="7 / 10" />);
    const answeredSize = screen.getByText("7 / 10").getAttribute("style");
    unmount();

    render(<PresentationMetric label="Time left" value={18} role="timer" />);
    const timeLeftSize = screen.getByText("18").getAttribute("style");

    expect(answeredSize).toBe(timeLeftSize);
  });

  it("uses the requested role, defaulting to status", () => {
    render(<PresentationMetric label="Answered" value="7 / 10" />);
    expect(screen.getByRole("status")).toBeInTheDocument();

    const { unmount } = render(<PresentationMetric label="Time left" value={18} role="timer" />);
    expect(screen.getByRole("timer")).toBeInTheDocument();
    unmount();
  });

  it("applies a distinct tone without relying on color alone in the DOM structure", () => {
    render(
      <PresentationMetric label="Answered" value="10 / 10" tone="success" role="status" />
    );
    expect(screen.getByRole("status").className).toContain("success");
  });
});
