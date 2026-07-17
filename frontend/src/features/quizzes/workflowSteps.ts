import type { ProgressStep } from "@/components/common/ProgressStepper";

/** The quiz authoring workflow's steps (Draft → Metadata → Questions → Review → Publish). */
export const AUTHORING_STEPS: ProgressStep[] = [
  { key: "metadata", label: "Metadata" },
  { key: "questions", label: "Questions" },
  { key: "review", label: "Review" }
];
