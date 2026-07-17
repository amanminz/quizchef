import { EntityStatusBadge, type EntityStatusTone } from "@/components/common/EntityStatusBadge";
import type { Difficulty } from "@/types/api";

const tones: Record<Difficulty, EntityStatusTone> = {
  EASY: "positive",
  MEDIUM: "warning",
  HARD: "critical"
};

const labels: Record<Difficulty, string> = {
  EASY: "Easy",
  MEDIUM: "Medium",
  HARD: "Hard"
};

export function DifficultyBadge({ difficulty }: { difficulty: Difficulty }) {
  return <EntityStatusBadge label={labels[difficulty]} tone={tones[difficulty]} />;
}
