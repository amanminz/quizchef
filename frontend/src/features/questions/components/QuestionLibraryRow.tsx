import { Link } from "react-router-dom";
import { Button } from "@/components/common/Button";
import { EntityStatusBadge } from "@/components/common/EntityStatusBadge";
import { editQuestionPath } from "@/features/questions/quizReturn";
import { DifficultyBadge } from "@/features/quizzes/components/DifficultyBadge";
import { LanguageChip } from "@/features/quizzes/components/LanguageChip";
import { lifecycleStateTone } from "@/features/quizzes/statusTone";
import { QUESTION_TYPES } from "@/features/questions/editorForm";
import type { QuestionSummaryResponse } from "@/types/api";

export interface QuestionLibraryRowProps {
  question: QuestionSummaryResponse;
  onPublish: (questionId: string) => void;
  onArchive: (questionId: string) => void;
  isPublishing?: boolean;
  isArchiving?: boolean;
}

/**
 * One library entry with its lifecycle actions: drafts can be edited and
 * published, published questions archived, archived ones only inspected.
 * The page owns the mutations (and the archive confirmation).
 */
export function QuestionLibraryRow({
  question,
  onPublish,
  onArchive,
  isPublishing,
  isArchiving
}: QuestionLibraryRowProps) {
  const questionId = question.id ?? "";
  const typeLabel = QUESTION_TYPES.find((type) => type.value === question.questionType)?.label;

  return (
    <div className="flex flex-wrap items-center gap-3 rounded-md border border-border px-3 py-2">
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{question.title}</p>
        <div className="mt-1 flex flex-wrap items-center gap-1.5">
          <EntityStatusBadge
            label={question.state ?? "DRAFT"}
            tone={lifecycleStateTone(question.state)}
          />
          {typeLabel && <span className="text-xs text-muted-foreground">{typeLabel}</span>}
          {question.difficulty && <DifficultyBadge difficulty={question.difficulty} />}
          {question.availableLanguages?.map((language) => (
            <LanguageChip key={language} language={language} />
          ))}
          {question.tags?.map((tag) => (
            <span key={tag.id} className="text-xs text-muted-foreground">
              #{tag.name}
            </span>
          ))}
        </div>
      </div>
      <div className="flex items-center gap-2">
        {question.state === "DRAFT" && (
          <>
            <Link to={editQuestionPath(questionId)}>
              <Button variant="secondary" size="sm">
                Edit
              </Button>
            </Link>
            <Button size="sm" isLoading={isPublishing} onClick={() => onPublish(questionId)}>
              Publish
            </Button>
          </>
        )}
        {question.state === "PUBLISHED" && (
          <Button
            variant="outline"
            size="sm"
            isLoading={isArchiving}
            onClick={() => onArchive(questionId)}
          >
            Archive
          </Button>
        )}
      </div>
    </div>
  );
}
