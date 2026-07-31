import { render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ProjectorCountdown } from "@/features/gameplay/components/ProjectorCountdown";

function endsInSeconds(seconds: number): string {
  return new Date(Date.now() + seconds * 1_000).toISOString();
}

describe("ProjectorCountdown", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-07-31T10:00:00Z"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("renders nothing while no question is open", () => {
    const { container } = render(<ProjectorCountdown endsAt={null} />);

    expect(container).toBeEmptyDOMElement();
  });

  it("shows TIME LEFT and the whole seconds remaining, with no urgency styling above 10s", () => {
    render(<ProjectorCountdown endsAt={endsInSeconds(18)} />);

    expect(screen.getByText(/time left/i)).toBeInTheDocument();
    expect(screen.getByText("18")).toBeInTheDocument();
    const timer = screen.getByRole("timer");
    expect(timer.className).not.toContain("destructive");
    expect(timer.className).not.toContain("amber");
  });

  it("adds a non-color warning treatment (icon + border) from 10 seconds remaining", () => {
    render(<ProjectorCountdown endsAt={endsInSeconds(10)} />);

    const timer = screen.getByRole("timer");
    expect(timer.className).toContain("amber");
    expect(timer.querySelector("svg")).toBeInTheDocument();
    expect(timer.className).not.toContain("motion-safe:animate-pulse");
  });

  it("adds a restrained, reduced-motion-safe pulse in the final 5 seconds", () => {
    render(<ProjectorCountdown endsAt={endsInSeconds(5)} />);

    const timer = screen.getByRole("timer");
    expect(timer.className.split(" ")).toContain("motion-safe:animate-pulse");
    expect(timer.className).toContain("destructive");
  });

  it("never depends on color alone — text and an icon accompany every urgency state", () => {
    render(<ProjectorCountdown endsAt={endsInSeconds(3)} />);

    expect(screen.getByText(/time left/i)).toBeInTheDocument();
    expect(screen.getByText("3")).toBeInTheDocument();
    expect(screen.getByRole("timer").querySelector("svg")).toBeInTheDocument();
  });

  it("keeps the digit box a fixed width regardless of how many digits are shown", () => {
    const { rerender } = render(<ProjectorCountdown endsAt={endsInSeconds(9)} />);
    const singleDigitClass = screen.getByText("9").className;
    expect(singleDigitClass).toContain("min-w-[3ch]");

    rerender(<ProjectorCountdown endsAt={endsInSeconds(120)} />);
    const tripleDigitClass = screen.getByText("120").className;
    expect(tripleDigitClass).toContain("min-w-[3ch]");
    expect(tripleDigitClass).toBe(singleDigitClass);
  });

  it("uses tabular numerals so digit width never shifts the surrounding layout", () => {
    render(<ProjectorCountdown endsAt={endsInSeconds(42)} />);

    expect(screen.getByText("42").className).toContain("tabular-nums");
  });
});
