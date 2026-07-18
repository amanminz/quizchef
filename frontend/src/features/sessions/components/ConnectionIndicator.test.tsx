import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ConnectionIndicator } from "@/features/sessions/components/ConnectionIndicator";

describe("ConnectionIndicator", () => {
  it("labels a live connection", () => {
    render(<ConnectionIndicator status="connected" />);

    expect(screen.getByText("Live")).toBeInTheDocument();
  });

  it("communicates retry state while reconnecting", () => {
    render(<ConnectionIndicator status="reconnecting" />);

    expect(screen.getByText("Reconnecting…")).toBeInTheDocument();
  });

  it("labels an in-progress first connection", () => {
    render(<ConnectionIndicator status="connecting" />);

    expect(screen.getByText("Connecting…")).toBeInTheDocument();
  });

  it("labels a dropped connection with no retry in flight", () => {
    render(<ConnectionIndicator status="disconnected" />);

    expect(screen.getByText("Offline")).toBeInTheDocument();
  });
});
