/** A slim progress bar for "how far through the quiz" — visual only, no numbers of its own. */
export function QuestionProgress({ number, total }: { number: number; total: number }) {
  const percent = total > 0 ? Math.min(100, Math.round((number / total) * 100)) : 0;
  return (
    <div
      role="progressbar"
      aria-valuenow={number}
      aria-valuemin={1}
      aria-valuemax={total}
      aria-label={`Question ${number} of ${total}`}
      className="h-1.5 w-full overflow-hidden rounded-full bg-muted"
    >
      <div
        className="h-full rounded-full bg-primary transition-[width] duration-500 ease-out"
        style={{ width: `${percent}%` }}
      />
    </div>
  );
}
