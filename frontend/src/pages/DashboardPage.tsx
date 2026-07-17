import { LayoutDashboard } from "lucide-react";
import { useCurrentUser } from "@/auth/useCurrentUser";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle
} from "@/components/common/Card";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { SectionHeader } from "@/components/common/SectionHeader";
import { Spinner } from "@/components/common/Spinner";

export function DashboardPage() {
  const { data: currentUser, isPending, isError, error, refetch } = useCurrentUser();

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
        <div className="grid gap-6 sm:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>Signed in</CardTitle>
              <CardDescription>Identity {currentUser.identityId}</CardDescription>
            </CardHeader>
            <CardContent className="text-sm text-muted-foreground">
              <p>Type: {currentUser.identityType}</p>
              <p>Roles: {currentUser.roles?.join(", ")}</p>
            </CardContent>
          </Card>
          <EmptyState
            icon={LayoutDashboard}
            title="Nothing here yet"
            description="Quiz authoring and live sessions arrive in the next frontend PRs."
          />
        </div>
      )}
    </PageContainer>
  );
}
