import { Link } from "react-router-dom";
import { Button } from "@/components/common/Button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/common/Card";
import { SessionStatusBadge } from "@/features/sessions/components/SessionStatusBadge";
import { useQuizTitle } from "@/features/sessions/hooks/useQuizTitle";
import type { SessionSummaryResponse } from "@/types/api";

export interface SessionCardProps {
  session: SessionSummaryResponse;
  onOpenLobby: (sessionPin: string) => void;
  isOpeningLobby?: boolean;
}

/**
 * One session on the hosting dashboard. Actions follow the lifecycle
 * (RFC-004): a CREATED session opens its lobby, a LOBBY session is
 * entered, an IN_PROGRESS session is resumed, a FINISHED session is
 * viewed. There is no cancel — the backend has no cancel endpoint;
 * unstarted sessions expire server-side.
 */
export function SessionCard({ session, onOpenLobby, isOpeningLobby }: SessionCardProps) {
  const sessionId = session.sessionId ?? "";
  const quizTitle = useQuizTitle(session.publishedQuizVersionId);

  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between gap-2">
          <CardTitle className="line-clamp-1">{quizTitle ?? "Quiz"}</CardTitle>
          <SessionStatusBadge state={session.state} />
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-2 text-sm text-muted-foreground">
        <div className="flex flex-wrap gap-x-4 gap-y-1">
          <span className="font-mono font-semibold text-foreground">
            {session.sessionPin ?? "——"}
          </span>
          <span>
            {session.participantCount ?? 0} participant
            {session.participantCount === 1 ? "" : "s"}
          </span>
          {session.createdAt && (
            <span>Created {new Date(session.createdAt).toLocaleString()}</span>
          )}
        </div>
      </CardContent>
      <CardFooter>
        {session.state === "CREATED" && (
          <>
            <Button
              size="sm"
              onClick={() => session.sessionPin && onOpenLobby(session.sessionPin)}
              isLoading={isOpeningLobby}
            >
              Open Lobby
            </Button>
            <Link to={`/sessions/${sessionId}`}>
              <Button variant="secondary" size="sm">
                Details
              </Button>
            </Link>
          </>
        )}
        {session.state === "LOBBY" && (
          <>
            <Link to={`/sessions/${sessionId}/lobby`}>
              <Button size="sm">Enter Lobby</Button>
            </Link>
            <Link to={`/sessions/${sessionId}`}>
              <Button variant="secondary" size="sm">
                Details
              </Button>
            </Link>
          </>
        )}
        {session.state === "IN_PROGRESS" && (
          <Link to={`/sessions/${sessionId}/play`}>
            <Button size="sm">Resume</Button>
          </Link>
        )}
        {(session.state === "FINISHED" || session.state === "ARCHIVED") && (
          <Link to={`/sessions/${sessionId}`}>
            <Button variant="secondary" size="sm">
              View Summary
            </Button>
          </Link>
        )}
      </CardFooter>
    </Card>
  );
}
