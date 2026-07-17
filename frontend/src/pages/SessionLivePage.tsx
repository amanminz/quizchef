import { Gamepad2 } from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { Button } from "@/components/common/Button";
import { EmptyState } from "@/components/common/EmptyState";
import { PageContainer } from "@/components/common/PageContainer";
import { WorkflowHeader } from "@/components/common/WorkflowHeader";
import { SessionStatusBadge } from "@/features/sessions/components/SessionStatusBadge";
import { useQuizTitle } from "@/features/sessions/hooks/useQuizTitle";
import { useSession } from "@/features/sessions/hooks/useSession";

/**
 * The gameplay route, as a deliberate placeholder: this PR ends at "the
 * host successfully launches a live session" — question presentation,
 * timers, and the live leaderboard are Phase 2 PR #4. The session is
 * genuinely IN_PROGRESS server-side when this renders.
 */
export function SessionLivePage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const { data: session } = useSession(sessionId);
  const quizTitle = useQuizTitle(session?.publishedQuizVersionId);

  return (
    <PageContainer>
      <WorkflowHeader
        title={quizTitle ?? "Live session"}
        backHref="/sessions"
        backLabel="Back to sessions"
        status={<SessionStatusBadge state={session?.state} />}
      />
      <EmptyState
        icon={Gamepad2}
        title="The session is live"
        description="Gameplay controls — presenting questions, closing answers, revealing results, and the leaderboard — arrive with the Live Gameplay Experience PR."
        action={
          <Link to="/sessions">
            <Button variant="secondary">Back to sessions</Button>
          </Link>
        }
      />
    </PageContainer>
  );
}
