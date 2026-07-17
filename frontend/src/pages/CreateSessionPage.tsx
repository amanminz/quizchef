import { ErrorPanel } from "@/components/common/ErrorPanel";
import { Button } from "@/components/common/Button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle
} from "@/components/common/Card";
import { PageContainer } from "@/components/common/PageContainer";
import { WorkflowHeader } from "@/components/common/WorkflowHeader";
import { LanguageChip } from "@/features/quizzes/components/LanguageChip";
import { ConfigurationSection } from "@/features/sessions/components/ConfigurationSection";
import { CountdownBadge } from "@/features/sessions/components/CountdownBadge";
import { QuizSelector } from "@/features/sessions/components/QuizSelector";
import { useSessionHosting } from "@/features/sessions/hooks/useSessionHosting";

/**
 * Create Session — step one of the hosting workflow. Pick a published
 * quiz, review what will run, create. The server assigns PIN and
 * settings; validation is server-authoritative (a quiz unpublished
 * between listing and create surfaces as the 409 it is).
 */
export function CreateSessionPage() {
  const hosting = useSessionHosting();

  return (
    <PageContainer>
      <WorkflowHeader
        title="Host a Session"
        backHref="/sessions"
        backLabel="Back to sessions"
        actions={
          <Button
            onClick={() => void hosting.createSession()}
            disabled={!hosting.selectedQuizId}
            isLoading={hosting.isCreating}
          >
            Create Session
          </Button>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[2fr_1fr]">
        <section aria-label="Choose a published quiz">
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted-foreground">
            Published quizzes
          </h2>
          {hosting.quizzesError != null ? (
            <ErrorPanel
              error={hosting.quizzesError}
              onRetry={() => void hosting.refetchQuizzes()}
            />
          ) : (
            <QuizSelector
              quizzes={hosting.publishedQuizzes}
              isLoading={hosting.isLoadingQuizzes}
              search={hosting.search}
              onSearchChange={hosting.setSearch}
              selectedQuizId={hosting.selectedQuizId}
              onSelect={hosting.selectQuiz}
            />
          )}
        </section>

        <div className="flex flex-col gap-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Selected quiz</CardTitle>
              {!hosting.selectedQuizId && (
                <CardDescription>Select a quiz to see its details.</CardDescription>
              )}
            </CardHeader>
            {hosting.selectedQuiz && (
              <CardContent className="flex flex-col gap-3 text-sm">
                <div className="flex flex-wrap items-center gap-2">
                  {hosting.selectedQuiz.defaultLanguage && (
                    <LanguageChip language={hosting.selectedQuiz.defaultLanguage} />
                  )}
                  <CountdownBadge seconds={hosting.secondsPerQuestion} />
                </div>
                <p className="text-muted-foreground">
                  {hosting.questionCount} question{hosting.questionCount === 1 ? "" : "s"}
                  {hosting.estimatedDurationMinutes != null && (
                    <> · about {hosting.estimatedDurationMinutes} min</>
                  )}
                </p>
              </CardContent>
            )}
          </Card>

          <ConfigurationSection settings={undefined} />

          {hosting.createError != null && (
            <ErrorPanel title="Could not create the session" error={hosting.createError} />
          )}
        </div>
      </div>
    </PageContainer>
  );
}
