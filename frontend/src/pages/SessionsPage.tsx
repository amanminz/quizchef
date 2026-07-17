import { Plus } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/common/Button";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { SectionHeader } from "@/components/common/SectionHeader";
import { Spinner } from "@/components/common/Spinner";
import { EmptySessionsState } from "@/features/sessions/components/EmptySessionsState";
import { SessionCard } from "@/features/sessions/components/SessionCard";
import { useHostControls } from "@/features/sessions/hooks/useHostControls";
import { useSessions } from "@/features/sessions/hooks/useSessions";
import type { SessionState } from "@/features/sessions/types";
import type { SessionSummaryResponse } from "@/types/api";

const SECTIONS: { states: SessionState[]; title: string }[] = [
  { states: ["IN_PROGRESS"], title: "In Progress" },
  { states: ["LOBBY"], title: "Waiting" },
  { states: ["CREATED"], title: "Scheduled" },
  { states: ["FINISHED", "ARCHIVED"], title: "Completed" }
];

/**
 * The hosting dashboard: sessions this browser created, sectioned by
 * lifecycle. The list is the local hosted-session registry hydrated per
 * id — the backend has no session list endpoint yet (see
 * hostedSessionsStore); expired sessions prune themselves.
 */
export function SessionsPage() {
  const navigate = useNavigate();
  const { sessions, isPending, error, refetch } = useSessions();
  const { openLobby, isOpeningLobby } = useHostControls(undefined);

  const openLobbyAndEnter = async (session: SessionSummaryResponse, sessionPin: string) => {
    // Failure surfaces through the mutation's error state; stay on the list.
    const opened = await openLobby(sessionPin).catch(() => undefined);
    if (opened) {
      navigate(`/sessions/${session.sessionId}/lobby`);
    }
  };

  const byStates = (states: SessionState[]): SessionSummaryResponse[] =>
    sessions.filter((session) => session.state && states.includes(session.state));

  return (
    <PageContainer>
      <SectionHeader
        title="Sessions"
        description="Host and monitor live sessions from this browser."
        actions={
          <Link to="/sessions/new">
            <Button size="sm">
              <Plus aria-hidden className="h-4 w-4" />
              New Session
            </Button>
          </Link>
        }
      />

      {isPending && (
        <div className="flex justify-center py-16">
          <Spinner size="lg" className="text-primary" />
        </div>
      )}

      {error != null && <ErrorPanel error={error} onRetry={refetch} />}

      {!isPending && error == null && sessions.length === 0 && <EmptySessionsState />}

      {!isPending && sessions.length > 0 && (
        <div className="flex flex-col gap-8">
          {SECTIONS.map((section) => {
            const sectionSessions = byStates(section.states);
            if (sectionSessions.length === 0) {
              return null;
            }
            return (
              <section key={section.title}>
                <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
                  {section.title}
                </h2>
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  {sectionSessions.map((session) => (
                    <SessionCard
                      key={session.sessionId}
                      session={session}
                      onOpenLobby={(pin) => void openLobbyAndEnter(session, pin)}
                      isOpeningLobby={isOpeningLobby}
                    />
                  ))}
                </div>
              </section>
            );
          })}
        </div>
      )}
    </PageContainer>
  );
}
