import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useParams } from "react-router-dom";
import { z } from "zod";
import { errorMessage } from "@/api/apiError";
import { Button } from "@/components/common/Button";
import { Card, CardContent } from "@/components/common/Card";
import { EntityStatusBadge } from "@/components/common/EntityStatusBadge";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { PageContainer } from "@/components/common/PageContainer";
import { ProgressStepper } from "@/components/common/ProgressStepper";
import { Spinner } from "@/components/common/Spinner";
import { WorkflowHeader } from "@/components/common/WorkflowHeader";
import { FormField } from "@/components/forms/FormField";
import { useQuizAuthoring } from "@/features/quizzes/hooks/useQuizAuthoring";
import { lifecycleStateTone } from "@/features/quizzes/statusTone";
import { AUTHORING_STEPS } from "@/features/quizzes/workflowSteps";
import { titleSchema, zodForm } from "@/utils/validation";

const editMetadataSchema = z.object({
  title: titleSchema,
  description: z.string().trim().max(1000, "Description is too long").optional(),
  visibility: z.enum(["PRIVATE", "UNLISTED", "PUBLIC"])
});

type EditMetadataForm = z.infer<typeof editMetadataSchema>;

export function EditQuizMetadataPage() {
  const { quizId } = useParams<{ quizId: string }>();
  const { quiz, isLoading, error, update, isUpdating } = useQuizAuthoring(quizId);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const isDraft = quiz?.state === "DRAFT";
  const isArchived = quiz?.state === "ARCHIVED";
  const defaultLocalization = quiz?.localizations?.find(
    (localization) => localization.languageCode === quiz.defaultLanguage
  );

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors }
  } = useForm<EditMetadataForm>(zodForm(editMetadataSchema));

  useEffect(() => {
    if (quiz) {
      reset({
        title: defaultLocalization?.title ?? "",
        description: defaultLocalization?.description ?? "",
        visibility: quiz.visibility ?? "PRIVATE"
      });
    }
    // reset only when the loaded quiz identity changes, not on every render
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [quiz?.id, quiz?.version]);

  const onSubmit = handleSubmit(async (values) => {
    if (!quiz) {
      return;
    }
    setSubmitError(null);
    try {
      const otherLocalizations = (quiz.localizations ?? []).filter(
        (localization) => localization.languageCode !== quiz.defaultLanguage
      );
      await update({
        version: quiz.version ?? 0,
        visibility: values.visibility,
        localizations: isDraft
          ? [
              ...otherLocalizations,
              {
                languageCode: quiz.defaultLanguage,
                title: values.title,
                description: values.description || undefined
              }
            ]
          : undefined
      });
    } catch (error) {
      setSubmitError(errorMessage(error));
    }
  });

  if (isLoading) {
    return (
      <PageContainer>
        <div className="flex justify-center py-16">
          <Spinner size="lg" className="text-primary" />
        </div>
      </PageContainer>
    );
  }

  if (error || !quiz) {
    return (
      <PageContainer>
        <ErrorPanel error={error} />
      </PageContainer>
    );
  }

  return (
    <PageContainer className="max-w-2xl">
      <WorkflowHeader
        title={defaultLocalization?.title ?? "Quiz"}
        backHref="/quizzes"
        backLabel="My Quizzes"
        status={<EntityStatusBadge label={quiz.state ?? "DRAFT"} tone={lifecycleStateTone(quiz.state)} />}
      />
      <ProgressStepper steps={AUTHORING_STEPS} currentKey="metadata" className="mb-6" />

      <Card>
        <CardContent className="pt-6">
          <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
            <FormField
              label="Title"
              disabled={!isDraft}
              error={errors.title?.message}
              {...register("title")}
            />

            <div className="flex flex-col gap-1.5">
              <label htmlFor="description" className="text-sm font-medium">
                Description
              </label>
              <textarea
                id="description"
                rows={3}
                disabled={!isDraft}
                className="rounded-md border border-input bg-background px-3 py-2 text-sm disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                {...register("description")}
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <span className="text-sm font-medium">Default language</span>
              <p className="text-sm text-muted-foreground">
                {quiz.defaultLanguage?.toUpperCase()} — set at creation, cannot be changed
              </p>
            </div>

            <div className="flex flex-col gap-1.5">
              <label htmlFor="visibility" className="text-sm font-medium">
                Visibility
              </label>
              <select
                id="visibility"
                disabled={isArchived}
                className="h-10 rounded-md border border-input bg-background px-3 text-sm disabled:opacity-50"
                {...register("visibility")}
              >
                <option value="PRIVATE">Private</option>
                <option value="UNLISTED">Unlisted</option>
                <option value="PUBLIC">Public</option>
              </select>
            </div>

            {submitError && (
              <p role="alert" className="text-sm text-destructive">
                {submitError}
              </p>
            )}

            {isArchived ? (
              <p className="text-sm text-muted-foreground">Archived quizzes are read-only.</p>
            ) : (
              <div className="flex items-center gap-2">
                <Button type="submit" isLoading={isUpdating}>
                  Save
                </Button>
                <Link to={`/quizzes/${quiz.id}/questions`}>
                  <Button type="button" variant="secondary">
                    Next: Questions
                  </Button>
                </Link>
              </div>
            )}
          </form>
        </CardContent>
      </Card>
    </PageContainer>
  );
}
