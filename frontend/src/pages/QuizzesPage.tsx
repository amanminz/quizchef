import { BookOpen, Plus } from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "@/components/common/Button";
import { EmptyState } from "@/components/common/EmptyState";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { SectionHeader } from "@/components/common/SectionHeader";
import { Spinner } from "@/components/common/Spinner";
import { useArchiveQuiz } from "@/features/quizzes/hooks/useArchiveQuiz";
import { usePublishQuiz } from "@/features/quizzes/hooks/usePublishQuiz";
import { useQuizzes } from "@/features/quizzes/hooks/useQuizzes";
import { QuizCard } from "@/features/quizzes/components/QuizCard";
import type { QuizState, QuizSummaryResponse } from "@/types/api";

const SECTIONS: { state: QuizState; title: string }[] = [
  { state: "DRAFT", title: "Draft" },
  { state: "PUBLISHED", title: "Published" },
  { state: "ARCHIVED", title: "Archived" }
];

export function QuizzesPage() {
  const { data, isPending, isError, error, refetch } = useQuizzes({ size: 100, sort: "updatedAt,desc" });
  const publishMutation = usePublishQuiz();
  const archiveMutation = useArchiveQuiz();

  const quizzes = data?.items ?? [];
  const byState = (state: QuizState): QuizSummaryResponse[] =>
    quizzes.filter((quiz) => quiz.state === state);

  return (
    <PageContainer>
      <SectionHeader
        title="Quizzes"
        description="Author and manage your quizzes."
        actions={
          <Link to="/quizzes/new">
            <Button size="sm">
              <Plus aria-hidden className="h-4 w-4" />
              New Quiz
            </Button>
          </Link>
        }
      />

      {isPending && (
        <div className="flex justify-center py-16">
          <Spinner size="lg" className="text-primary" />
        </div>
      )}

      {isError && <ErrorPanel error={error} onRetry={() => void refetch()} />}

      {data && quizzes.length === 0 && (
        <EmptyState
          icon={BookOpen}
          title="No quizzes yet"
          description="Create your first quiz to start building a live session."
          action={
            <Link to="/quizzes/new">
              <Button>New Quiz</Button>
            </Link>
          }
        />
      )}

      {data && quizzes.length > 0 && (
        <div className="flex flex-col gap-8">
          {SECTIONS.map((section) => {
            const sectionQuizzes = byState(section.state);
            if (sectionQuizzes.length === 0) {
              return null;
            }
            return (
              <section key={section.state}>
                <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
                  {section.title}
                </h2>
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  {sectionQuizzes.map((quiz) => (
                    <QuizCard
                      key={quiz.id}
                      quiz={quiz}
                      onPublish={(quizId) => publishMutation.mutate(quizId)}
                      isPublishing={publishMutation.isPending && publishMutation.variables === quiz.id}
                      onArchive={(quizId) => archiveMutation.mutate(quizId)}
                      isArchiving={archiveMutation.isPending && archiveMutation.variables === quiz.id}
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
