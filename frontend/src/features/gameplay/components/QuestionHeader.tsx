import { PresentationMetric } from "@/features/gameplay/components/PresentationMetric";
import { ProjectorCountdown } from "@/features/gameplay/components/ProjectorCountdown";
import { QuestionNumberBadge } from "@/features/gameplay/components/QuestionNumberBadge";
import { QuestionProgress } from "@/features/gameplay/components/QuestionProgress";
import { QuestionTimer } from "@/features/gameplay/components/QuestionTimer";
import type { AnswerProgressResponse } from "@/types/api";

export interface QuestionHeaderProps {
  number: number;
  total: number;
  /** The server's close time; omit while the question isn't open. */
  endsAt?: string | null;
  /** Presentation Mode swaps the compact timer for the projector-scale one. */
  presentationActive?: boolean;
  /** The reading period: shows an "Answered –" placeholder, not the box disappearing. */
  previewing?: boolean;
  /** The backend's answered/eligible counts, shown compactly in Presentation Mode. */
  answerProgress?: AnswerProgressResponse;
  /** Everyone eligible has answered — the moment worth emphasizing. */
  emphasized?: boolean;
}

/**
 * Position in the quiz, the live timer, and — in Presentation Mode — the
 * answered count, together in one compact row instead of a separate giant
 * centered block. "Answered" and "Time left" are both `PresentationMetric`
 * boxes, so they share identical sizing by construction. The progress bar
 * is dropped in Presentation Mode (non-essential content, per the
 * projector-layout hotfix's reduction order) — normal layout is unchanged.
 */
export function QuestionHeader({
  number,
  total,
  endsAt,
  presentationActive = false,
  previewing = false,
  answerProgress,
  emphasized = false
}: QuestionHeaderProps) {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <QuestionNumberBadge number={number} total={total} />
        {presentationActive ? (
          <div className="flex flex-wrap items-center justify-end gap-2">
            {(previewing || answerProgress) && (
              <PresentationMetric
                label="Answered"
                value={
                  answerProgress
                    ? `${answerProgress.answeredCount} / ${answerProgress.eligibleCount}`
                    : "–"
                }
                tone={answerProgress && emphasized ? "success" : "default"}
              />
            )}
            <ProjectorCountdown endsAt={endsAt} compact />
          </div>
        ) : (
          <QuestionTimer endsAt={endsAt} />
        )}
      </div>
      {!presentationActive && <QuestionProgress number={number} total={total} />}
    </div>
  );
}
