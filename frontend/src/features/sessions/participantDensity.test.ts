import { describe, expect, it } from "vitest";
import { participantDensity } from "@/features/sessions/participantDensity";

describe("participantDensity", () => {
  it("maps player counts onto the defined density tiers", () => {
    expect(participantDensity(0)).toBe("large");
    expect(participantDensity(10)).toBe("large");
    expect(participantDensity(11)).toBe("medium");
    expect(participantDensity(25)).toBe("medium");
    expect(participantDensity(26)).toBe("compact");
    expect(participantDensity(50)).toBe("compact");
    expect(participantDensity(51)).toBe("dense");
    expect(participantDensity(200)).toBe("dense");
  });
});
