import { PerformanceMetric } from "@/features/gameplay/components/PerformanceMetric";
import type { SessionResultsResponse } from "@/types/api";

/**
 * The session's closing figures, straight from the results read: how many
 * played, how many questions ran, and the top score. (Duration and
 * per-participant timing aren't in the contract — the session summary
 * carries no started/finished timestamps; noted in RFC-009 rather than
 * approximated client-side.)
 */
export function FinalStatistics({ results }: { results: SessionResultsResponse }) {
  const topScore = results.entries?.[0]?.score;
  return (
    <div className="grid grid-cols-3 gap-3">
      <PerformanceMetric label="Players" value={results.participantCount ?? 0} />
      <PerformanceMetric label="Questions" value={results.totalQuestions ?? 0} />
      <PerformanceMetric label="Top score" value={topScore ?? 0} />
    </div>
  );
}
