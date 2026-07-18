import { BookOpen } from "lucide-react";
import type { ReactNode } from "react";
import { EmptyState } from "@/components/common/EmptyState";

export interface EmptyLibraryStateProps {
  /** True when filters are active — changes the copy from "no questions yet" to "no matches". */
  isFiltered: boolean;
  /** CTA slot: "Create Question" when truly empty, "Clear filters" when filtered. */
  action?: ReactNode;
}

/** The question library's empty state — a preset of the generic EmptyState. */
export function EmptyLibraryState({ isFiltered, action }: EmptyLibraryStateProps) {
  return (
    <EmptyState
      icon={BookOpen}
      title={isFiltered ? "No questions match your filters" : "Your question library is empty"}
      description={
        isFiltered
          ? "Clear or adjust the filters and try again."
          : "Questions are authored once and reused across quizzes. Create your first question to start building this quiz."
      }
      action={action}
    />
  );
}
