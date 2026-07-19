import { Link, useNavigate, useParams } from "react-router-dom";
import { useCurrentUser } from "@/auth/useCurrentUser";
import { Button } from "@/components/common/Button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/common/Card";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { Spinner } from "@/components/common/Spinner";
import { WorkflowHeader } from "@/components/common/WorkflowHeader";
import { ConfigurationSection } from "@/features/sessions/components/ConfigurationSection";
import { JoinCodeCard } from "@/features/sessions/components/JoinCodeCard";
import { SessionStatusBadge } from "@/features/sessions/components/SessionStatusBadge";
import { useHostControls } from "@/features/sessions/hooks/useHostControls";
import { useQuizTitle } from "@/features/sessions/hooks/useQuizTitle";
import { useSession } from "@/features/sessions/hooks/useSession";

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-4 py-1.5 text-sm">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="font-medium">{value}</dd>
    </div>
  );
}

/**
 * Read-only session status view — the "configure" stop between creating a
 * session and opening its lobby. Everything shown is the server's summary;
 * the only commands are the lifecycle ones the backend offers (open lobby;
 * there is no cancel endpoint).
 */
export function SessionDetailsPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const { data: session, isPending, error, refetch } = useSession(sessionId);
  const { data: currentUser } = useCurrentUser();
  const quizTitle = useQuizTitle(session?.publishedQuizVersionId);
  const { canOpenLobby, openLobby, isOpeningLobby, openLobbyError } = useHostControls(session);

  const openLobbyAndEnter = async () => {
    if (!session?.sessionPin) {
      return;
    }
    // Failure surfaces through openLobbyError; stay on the details page.
    const opened = await openLobby(session.sessionPin).catch(() => undefined);
    if (opened) {
      navigate(`/sessions/${sessionId}/lobby`);
    }
  };

  const hostLabel =
    session?.hostIdentityId && session.hostIdentityId === currentUser?.identityId
      ? "You"
      : (session?.hostIdentityId ?? "Unknown");

  return (
    <PageContainer>
      <WorkflowHeader
        title={quizTitle ?? "Session"}
        backHref="/sessions"
        backLabel="Back to sessions"
        status={<SessionStatusBadge state={session?.state} />}
        actions={
          <>
            {canOpenLobby && (
              <Button onClick={() => void openLobbyAndEnter()} isLoading={isOpeningLobby}>
                Open Lobby
              </Button>
            )}
            {session?.state === "LOBBY" && (
              <Link to={`/sessions/${sessionId}/lobby`}>
                <Button>Enter Lobby</Button>
              </Link>
            )}
            {session?.state === "IN_PROGRESS" && (
              <Link to={`/sessions/${sessionId}/play`}>
                <Button>Resume</Button>
              </Link>
            )}
          </>
        }
      />

      {isPending && (
        <div className="flex justify-center py-16">
          <Spinner size="lg" className="text-primary" />
        </div>
      )}

      {error != null && <ErrorPanel error={error} onRetry={() => void refetch()} />}

      {openLobbyError != null && (
        <div className="mb-6">
          <ErrorPanel title="Could not open the lobby" error={openLobbyError} />
        </div>
      )}

      {session && (
        <div className="grid gap-6 lg:grid-cols-[1fr_1fr]">
          <div className="flex flex-col gap-4">
            <JoinCodeCard sessionPin={session.sessionPin} quizTitle={quizTitle} />
            <ConfigurationSection settings={session.settings} />
          </div>
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Session</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="divide-y">
                <DetailRow label="Host" value={hostLabel} />
                <DetailRow label="Quiz" value={quizTitle ?? "—"} />
                <DetailRow
                  label="Participants"
                  value={String(session.participantCount ?? 0)}
                />
                {session.createdAt && (
                  <DetailRow
                    label="Created"
                    value={new Date(session.createdAt).toLocaleString()}
                  />
                )}
              </dl>
            </CardContent>
          </Card>
        </div>
      )}
    </PageContainer>
  );
}
