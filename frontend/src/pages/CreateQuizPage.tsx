import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { z } from "zod";
import { errorMessage } from "@/api/apiError";
import { Button } from "@/components/common/Button";
import { Card, CardContent } from "@/components/common/Card";
import { PageContainer } from "@/components/common/PageContainer";
import { SectionHeader } from "@/components/common/SectionHeader";
import { FormField } from "@/components/forms/FormField";
import { useQuizAuthoring } from "@/features/quizzes/hooks/useQuizAuthoring";
import { languageCodeSchema, titleSchema, zodForm } from "@/utils/validation";

const LANGUAGES = [
  { value: "en", label: "English" },
  { value: "kn", label: "Kannada" },
  { value: "hi", label: "Hindi" },
  { value: "ta", label: "Tamil" },
  { value: "te", label: "Telugu" },
  { value: "ml", label: "Malayalam" }
];

const createQuizSchema = z.object({
  title: titleSchema,
  description: z.string().trim().max(1000, "Description is too long").optional(),
  defaultLanguage: languageCodeSchema
});

type CreateQuizForm = z.infer<typeof createQuizSchema>;

export function CreateQuizPage() {
  const navigate = useNavigate();
  const { create, isCreating } = useQuizAuthoring(undefined);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors }
  } = useForm<CreateQuizForm>(zodForm(createQuizSchema, { defaultValues: { defaultLanguage: "en" } }));

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null);
    try {
      const quiz = await create({
        defaultLanguage: values.defaultLanguage,
        localization: {
          languageCode: values.defaultLanguage,
          title: values.title,
          description: values.description || undefined
        }
      });
      if (quiz.id) {
        navigate(`/quizzes/${quiz.id}`, { replace: true });
      }
    } catch (error) {
      setSubmitError(errorMessage(error));
    }
  });

  return (
    <PageContainer className="max-w-2xl">
      <SectionHeader title="New Quiz" description="Start a draft — you'll add questions next." />
      <Card>
        <CardContent className="pt-6">
          <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
            <FormField label="Title" error={errors.title?.message} {...register("title")} />

            <div className="flex flex-col gap-1.5">
              <label htmlFor="description" className="text-sm font-medium">
                Description
              </label>
              <textarea
                id="description"
                rows={3}
                className="rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                {...register("description")}
              />
              {errors.description && (
                <p role="alert" className="text-sm text-destructive">
                  {errors.description.message}
                </p>
              )}
            </div>

            <div className="flex flex-col gap-1.5">
              <label htmlFor="defaultLanguage" className="text-sm font-medium">
                Default language
              </label>
              <select
                id="defaultLanguage"
                className="h-10 rounded-md border border-input bg-background px-3 text-sm"
                {...register("defaultLanguage")}
              >
                {LANGUAGES.map((language) => (
                  <option key={language.value} value={language.value}>
                    {language.label}
                  </option>
                ))}
              </select>
              {errors.defaultLanguage && (
                <p role="alert" className="text-sm text-destructive">
                  {errors.defaultLanguage.message}
                </p>
              )}
            </div>

            {submitError && (
              <p role="alert" className="text-sm text-destructive">
                {submitError}
              </p>
            )}

            <Button type="submit" isLoading={isCreating}>
              Create Quiz
            </Button>
          </form>
        </CardContent>
      </Card>
    </PageContainer>
  );
}
