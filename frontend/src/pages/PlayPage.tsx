import { Gamepad2 } from "lucide-react";
import { EmptyState } from "@/components/common/EmptyState";
import { PageContainer } from "@/components/common/PageContainer";

export function PlayPage() {
  return (
    <PageContainer className="py-16">
      <EmptyState
        icon={Gamepad2}
        title="Joining a game is coming"
        description="Enter-a-PIN and live gameplay arrive in a later Phase 2 PR."
      />
    </PageContainer>
  );
}
