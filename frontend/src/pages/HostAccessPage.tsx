import { PageContainer } from "@/components/common/PageContainer";
import { Spinner } from "@/components/common/Spinner";
import { WorkflowHeader } from "@/components/common/WorkflowHeader";
import { HostAccessCard } from "@/features/identity/components/HostAccessCard";
import { OnboardingProgress } from "@/features/identity/components/OnboardingProgress";
import { PromotionSuccess } from "@/features/identity/components/PromotionSuccess";
import { useHostAccess } from "@/features/identity/hooks/useHostAccess";

/**
 * The host onboarding flow. Server-confirmed throughout: the success
 * state renders only once the server has granted — and since the roles
 * live in the shared currentUser query, every role-aware surface
 * (navigation, dashboard) updates the moment the invalidation lands. A
 * host who navigates here again simply sees the success state.
 */
export function HostAccessPage() {
  const hostAccess = useHostAccess();

  return (
    <PageContainer className="max-w-2xl">
      <WorkflowHeader title="Become a Host" backHref="/profile" backLabel="Back to profile" />

      <div className="mb-6">
        <OnboardingProgress isHost={hostAccess.isHost} />
      </div>

      {hostAccess.isLoadingStatus && (
        <div className="flex justify-center py-16">
          <Spinner size="lg" className="text-primary" />
        </div>
      )}

      {!hostAccess.isLoadingStatus && hostAccess.isHost && <PromotionSuccess />}

      {!hostAccess.isLoadingStatus && !hostAccess.isHost && (
        <HostAccessCard
          onRequest={() => void hostAccess.requestHostAccess().catch(() => undefined)}
          isRequesting={hostAccess.isRequesting}
          error={hostAccess.requestError}
        />
      )}
    </PageContainer>
  );
}
