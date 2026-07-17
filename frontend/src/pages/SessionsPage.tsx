import { Radio } from "lucide-react";
import { EmptyState } from "@/components/common/EmptyState";
import { PageContainer } from "@/components/common/PageContainer";
import { SectionHeader } from "@/components/common/SectionHeader";

export function SessionsPage() {
  return (
    <PageContainer>
      <SectionHeader title="Sessions" description="Host and monitor live sessions." />
      <EmptyState
        icon={Radio}
        title="Session hosting is coming"
        description="This page gains the lobby and host controls in a later Phase 2 PR."
      />
    </PageContainer>
  );
}
