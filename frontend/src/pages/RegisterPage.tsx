import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { z } from "zod";
import { errorMessage } from "@/api/apiError";
import { identityApi } from "@/api/identityApi";
import { useAuth } from "@/auth/useAuth";
import { Button } from "@/components/common/Button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/common/Card";
import { PageContainer } from "@/components/common/PageContainer";
import { FormField } from "@/components/forms/FormField";
import { displayNameSchema, emailSchema, passwordSchema, zodForm } from "@/utils/validation";

const registerSchema = z.object({
  displayName: displayNameSchema,
  email: emailSchema,
  password: passwordSchema
});

type RegisterForm = z.infer<typeof registerSchema>;

/**
 * Account creation — the front door of host onboarding (Phase 3 PR #1).
 * Registration grants the durable USER role; a registered member becomes
 * a host from their profile. On success the new account is logged in
 * immediately (register issues no token by design — login owns sessions,
 * RFC-002), landing on the dashboard's member view.
 */
export function RegisterPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [submitError, setSubmitError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting }
  } = useForm<RegisterForm>(zodForm(registerSchema));

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null);
    try {
      await identityApi.register({
        displayName: values.displayName,
        email: values.email,
        password: values.password
      });
      await login(values.email, values.password);
      navigate("/dashboard", { replace: true });
    } catch (error) {
      setSubmitError(errorMessage(error));
    }
  });

  return (
    <PageContainer className="flex justify-center py-16">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Create your account</CardTitle>
          <CardDescription>Play, and become a host whenever you're ready.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
            <FormField
              label="Display name"
              autoComplete="name"
              error={errors.displayName?.message}
              {...register("displayName")}
            />
            <FormField
              label="Email"
              type="email"
              autoComplete="email"
              error={errors.email?.message}
              {...register("email")}
            />
            <FormField
              label="Password"
              type="password"
              autoComplete="new-password"
              error={errors.password?.message}
              {...register("password")}
            />
            {submitError && (
              <p role="alert" className="text-sm text-destructive">
                {submitError}
              </p>
            )}
            <Button type="submit" isLoading={isSubmitting}>
              Create account
            </Button>
            <p className="text-center text-sm text-muted-foreground">
              Already have an account?{" "}
              <Link to="/login" className="font-medium text-primary hover:underline">
                Sign in
              </Link>
            </p>
          </form>
        </CardContent>
      </Card>
    </PageContainer>
  );
}
