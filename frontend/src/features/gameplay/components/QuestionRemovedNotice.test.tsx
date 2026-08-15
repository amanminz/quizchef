import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { QuestionRemovedNotice } from "@/features/gameplay/components/QuestionRemovedNotice";

describe("QuestionRemovedNotice", () => {
  it("explains the removal in both event languages at once", () => {
    render(<QuestionRemovedNotice />);

    // Not the device's chosen language: this interrupts a game in progress
    // to explain why the question vanished, and a player who misreads that
    // has no way to ask.
    expect(screen.getByText("This question was removed by the Quiz Master.")).toBeInTheDocument();
    expect(screen.getByText("Get ready for the next question.")).toBeInTheDocument();
    expect(screen.getByText("क्विज़ मास्टर ने इस सवाल को हटा दिया है।")).toBeInTheDocument();
    expect(screen.getByText("अगले सवाल के लिए तैयार हो जाइए।")).toBeInTheDocument();
  });

  it("is announced to assistive technology without stealing focus", () => {
    render(<QuestionRemovedNotice />);

    expect(screen.getByRole("status")).toBeInTheDocument();
  });
});
