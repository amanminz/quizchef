/** One labeled figure on a results screen (participants, questions, a score…). */
export function PerformanceMetric({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="flex flex-col items-center gap-0.5 rounded-lg border px-4 py-3 text-center">
      <span className="font-mono text-xl font-bold tabular-nums">{value}</span>
      <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {label}
      </span>
    </div>
  );
}
