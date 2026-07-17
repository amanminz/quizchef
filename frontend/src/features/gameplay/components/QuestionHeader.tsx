import { QuestionNumberBadge } from "@/features/gameplay/components/QuestionNumberBadge";
import { QuestionProgress } from "@/features/gameplay/components/QuestionProgress";
import { QuestionTimer } from "@/features/gameplay/components/QuestionTimer";

export interface QuestionHeaderProps {
  number: number;
  total: number;
  /** The server's close time; omit while the question isn't open. */
  endsAt?: string | null;
}

/** Position in the quiz, progress bar, and the live timer, together. */
export function QuestionHeader({ number, total, endsAt }: QuestionHeaderProps) {
  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between gap-3">
        <QuestionNumberBadge number={number} total={total} />
        <QuestionTimer endsAt={endsAt} />
      </div>
      <QuestionProgress number={number} total={total} />
    </div>
  );
}
