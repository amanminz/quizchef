import { Link } from "react-router-dom";
import { Button } from "@/components/common/Button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/common/Card";
import { EntityStatusBadge } from "@/components/common/EntityStatusBadge";
import { lifecycleStateTone } from "@/features/quizzes/statusTone";
import type { QuizSummaryResponse } from "@/types/api";

export interface QuizCardProps {
  quiz: QuizSummaryResponse;
  onPublish: (quizId: string) => void;
  isPublishing?: boolean;
  onArchive: (quizId: string) => void;
  isArchiving?: boolean;
}

/**
 * One row of "My Quizzes". Presentational — the actions call back to the
 * page, which owns the mutations (useQuizzes/usePublishQuiz/useArchiveQuiz)
 * so this card stays easy to render and test in isolation.
 *
 * Actions follow the quiz lifecycle exactly (RFC-003): a draft is edited
 * or published, never deleted — there is no delete endpoint, by design.
 * "Duplicate" on an archived quiz is a documented future feature, shown
 * disabled rather than omitted or faked.
 */
export function QuizCard({ quiz, onPublish, isPublishing, onArchive, isArchiving }: QuizCardProps) {
  const quizId = quiz.id ?? "";

  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between gap-2">
          <CardTitle className="line-clamp-1">{quiz.title}</CardTitle>
          <EntityStatusBadge label={quiz.state ?? "DRAFT"} tone={lifecycleStateTone(quiz.state)} />
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-2 text-sm text-muted-foreground">
        {quiz.description && <p className="line-clamp-2">{quiz.description}</p>}
        <div className="flex flex-wrap gap-x-4 gap-y-1">
          <span>{quiz.defaultLanguage?.toUpperCase()}</span>
          <span>
            {quiz.questionCount ?? 0} question{quiz.questionCount === 1 ? "" : "s"}
          </span>
          {quiz.updatedAt && <span>Updated {new Date(quiz.updatedAt).toLocaleDateString()}</span>}
        </div>
      </CardContent>
      <CardFooter>
        {quiz.state === "DRAFT" && (
          <>
            <Link to={`/quizzes/${quizId}`}>
              <Button variant="secondary" size="sm">
                Edit
              </Button>
            </Link>
            <Button size="sm" onClick={() => onPublish(quizId)} isLoading={isPublishing}>
              Publish
            </Button>
          </>
        )}
        {quiz.state === "PUBLISHED" && (
          <>
            <Link to={`/quizzes/${quizId}/review`}>
              <Button variant="secondary" size="sm">
                Review
              </Button>
            </Link>
            <Button
              variant="destructive"
              size="sm"
              onClick={() => onArchive(quizId)}
              isLoading={isArchiving}
            >
              Archive
            </Button>
          </>
        )}
        {quiz.state === "ARCHIVED" && (
          <>
            <Link to={`/quizzes/${quizId}/review`}>
              <Button variant="secondary" size="sm">
                View
              </Button>
            </Link>
            <Button variant="ghost" size="sm" disabled title="Coming soon">
              Duplicate
            </Button>
          </>
        )}
      </CardFooter>
    </Card>
  );
}
