import { BookOpen, Radio, UserRound } from "lucide-react";
import { Link } from "react-router-dom";
import { useCurrentUser } from "@/auth/useCurrentUser";
import { Button } from "@/components/common/Button";
import {
  Card,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle
} from "@/components/common/Card";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { SectionHeader } from "@/components/common/SectionHeader";
import { Spinner } from "@/components/common/Spinner";
import { RoleBadge } from "@/features/identity/components/RoleBadge";
import { usePermissions } from "@/features/identity/hooks/usePermissions";

/**
 * Home, adapted to what the account can actually do (RFC-009: cosmetic —
 * the backend is the authority either way): a member sees the path to
 * hosting; a host sees the authoring and hosting workflows.
 */
export function DashboardPage() {
  const { data: currentUser, isPending, isError, error, refetch } = useCurrentUser();
  const { isHost } = usePermissions();

  return (
    <PageContainer>
      <SectionHeader title="Dashboard" description="Your QuizChef home." />
      {isPending && (
        <div className="flex justify-center py-16">
          <Spinner size="lg" className="text-primary" />
        </div>
      )}
      {isError && <ErrorPanel error={error} onRetry={() => void refetch()} />}
      {currentUser && (
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {isHost ? (
            <>
              <Card>
                <CardHeader>
                  <Radio aria-hidden className="mb-1 h-6 w-6 text-primary" />
                  <CardTitle>Host a session</CardTitle>
                  <CardDescription>
                    Run a published quiz live — open a lobby, share the code, start when everyone
                    is in.
                  </CardDescription>
                </CardHeader>
                <CardFooter>
                  <Link to="/sessions/new">
                    <Button size="sm">New Session</Button>
                  </Link>
                  <Link to="/sessions">
                    <Button variant="secondary" size="sm">
                      My Sessions
                    </Button>
                  </Link>
                </CardFooter>
              </Card>
              <Card>
                <CardHeader>
                  <BookOpen aria-hidden className="mb-1 h-6 w-6 text-primary" />
                  <CardTitle>Author quizzes</CardTitle>
                  <CardDescription>
                    Create quizzes, compose questions, and publish when they are ready to host.
                  </CardDescription>
                </CardHeader>
                <CardFooter>
                  <Link to="/quizzes/new">
                    <Button size="sm">New Quiz</Button>
                  </Link>
                  <Link to="/quizzes">
                    <Button variant="secondary" size="sm">
                      My Quizzes
                    </Button>
                  </Link>
                </CardFooter>
              </Card>
            </>
          ) : (
            <Card>
              <CardHeader>
                <Radio aria-hidden className="mb-1 h-6 w-6 text-primary" />
                <CardTitle>Become a Host</CardTitle>
                <CardDescription>
                  Hosting unlocks quiz authoring and live sessions — it takes one click.
                </CardDescription>
              </CardHeader>
              <CardFooter>
                <Link to="/profile/host-access">
                  <Button size="sm">Become a Host</Button>
                </Link>
              </CardFooter>
            </Card>
          )}
          <Card>
            <CardHeader>
              <UserRound aria-hidden className="mb-1 h-6 w-6 text-primary" />
              <div className="flex items-start justify-between gap-2">
                <CardTitle>{currentUser.displayName ?? "Your account"}</CardTitle>
                <span className="flex flex-wrap gap-1.5">
                  {(currentUser.roles ?? []).map((role) => (
                    <RoleBadge key={role} role={role} />
                  ))}
                </span>
              </div>
              {currentUser.email && <CardDescription>{currentUser.email}</CardDescription>}
            </CardHeader>
            <CardFooter>
              <Link to="/profile">
                <Button variant="secondary" size="sm">
                  View Profile
                </Button>
              </Link>
            </CardFooter>
          </Card>
        </div>
      )}
    </PageContainer>
  );
}
