import { BookOpen } from "lucide-react";
import { EmptyState } from "@/components/common/EmptyState";

export interface EmptyLibraryStateProps {
  /** True when filters are active — changes the copy from "no questions yet" to "no matches". */
  isFiltered: boolean;
}

/** The question library's empty state — a preset of the generic EmptyState. */
export function EmptyLibraryState({ isFiltered }: EmptyLibraryStateProps) {
  return (
    <EmptyState
      icon={BookOpen}
      title={isFiltered ? "No questions match these filters" : "Your question library is empty"}
      description={
        isFiltered
          ? "Try a different search term or clear the filters."
          : "Questions are authored once and reused across quizzes. Publish a question to make it attachable here."
      }
    />
  );
}
