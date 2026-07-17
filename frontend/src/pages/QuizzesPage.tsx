import { BookOpen } from "lucide-react";
import { EmptyState } from "@/components/common/EmptyState";
import { PageContainer } from "@/components/common/PageContainer";
import { SectionHeader } from "@/components/common/SectionHeader";

export function QuizzesPage() {
  return (
    <PageContainer>
      <SectionHeader title="Quizzes" description="Author and manage your quizzes." />
      <EmptyState
        icon={BookOpen}
        title="Quiz authoring is coming"
        description="This page gains the quiz builder in Phase 2 PR #2."
      />
    </PageContainer>
  );
}
