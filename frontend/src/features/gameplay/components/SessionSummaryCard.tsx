import type { ReactNode } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/common/Card";
import type { SessionResultsResponse } from "@/types/api";

export interface SessionSummaryCardProps {
  /** The quiz's title — resolvable by the host (quiz owner); omitted for guests. */
  quizTitle?: string;
  results: SessionResultsResponse;
  /** Return-to-dashboard / play-again actions (PlayAgainCard). */
  actions?: ReactNode;
}

function SummaryRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 py-1.5 text-sm">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-medium">{value}</dd>
    </div>
  );
}

/** The session's closing summary — what ran, who won, how it ended. */
export function SessionSummaryCard({ quizTitle, results, actions }: SessionSummaryCardProps) {
  const winner = results.entries?.[0];
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{quizTitle ?? "Session summary"}</CardTitle>
        <CardDescription>The quiz has finished.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <dl className="divide-y">
          <SummaryRow label="Questions" value={String(results.totalQuestions ?? 0)} />
          <SummaryRow label="Participants" value={String(results.participantCount ?? 0)} />
          {winner?.displayName && (
            <SummaryRow label="Winner" value={`${winner.displayName} · ${winner.score} pts`} />
          )}
        </dl>
        {actions}
      </CardContent>
    </Card>
  );
}
