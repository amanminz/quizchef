import { Link } from "react-router-dom";
import { Button } from "@/components/common/Button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/common/Card";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { SectionHeader } from "@/components/common/SectionHeader";
import { Spinner } from "@/components/common/Spinner";
import { OnboardingProgress } from "@/features/identity/components/OnboardingProgress";
import { ProfileCard } from "@/features/identity/components/ProfileCard";
import { usePermissions } from "@/features/identity/hooks/usePermissions";
import { useProfile } from "@/features/identity/hooks/useProfile";

/** The caller's own account: profile, durable roles, and the path to hosting. */
export function ProfilePage() {
  const profile = useProfile();
  const { isHost } = usePermissions();

  return (
    <PageContainer className="max-w-2xl">
      <SectionHeader title="Profile" description="Your account and what it can do." />

      {profile.isPending && (
        <div className="flex justify-center py-16">
          <Spinner size="lg" className="text-primary" />
        </div>
      )}

      {profile.isError && <ErrorPanel error={profile.error} onRetry={() => void profile.refetch()} />}

      {!profile.isPending && !profile.isError && (
        <div className="flex flex-col gap-6">
          <ProfileCard
            displayName={profile.displayName ?? undefined}
            email={profile.email ?? undefined}
            identityType={profile.identityType}
            roles={profile.roles}
          />

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Hosting</CardTitle>
              <CardDescription>
                {isHost
                  ? "You can author quizzes and run live sessions."
                  : "Become a host to author quizzes and run live sessions."}
              </CardDescription>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              <OnboardingProgress isHost={isHost} />
              {!isHost && (
                <Link to="/profile/host-access" className="self-start">
                  <Button size="sm">Become a Host</Button>
                </Link>
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </PageContainer>
  );
}
