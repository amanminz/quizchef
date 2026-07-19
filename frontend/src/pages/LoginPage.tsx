import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { z } from "zod";
import { errorMessage } from "@/api/apiError";
import { useAuth } from "@/auth/useAuth";
import { useAuthStore } from "@/auth/authStore";
import { Button } from "@/components/common/Button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/common/Card";
import { PageContainer } from "@/components/common/PageContainer";
import { FormField } from "@/components/forms/FormField";
import { emailSchema, passwordSchema, zodForm } from "@/utils/validation";

const loginSchema = z.object({
  email: emailSchema,
  password: passwordSchema
});

type LoginForm = z.infer<typeof loginSchema>;

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const sessionExpired = useAuthStore((state) => state.sessionExpired);
  const acknowledgeSessionExpired = useAuthStore((state) => state.acknowledgeSessionExpired);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting }
  } = useForm<LoginForm>(zodForm(loginSchema));

  const from = (location.state as { from?: string } | null)?.from ?? "/dashboard";

  const onSubmit = handleSubmit(async (values) => {
    setSubmitError(null);
    try {
      await login(values.email, values.password);
      acknowledgeSessionExpired();
      navigate(from, { replace: true });
    } catch (error) {
      setSubmitError(errorMessage(error));
    }
  });

  return (
    <PageContainer className="flex justify-center py-16">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Sign in</CardTitle>
        </CardHeader>
        <CardContent>
          {sessionExpired && (
            <p
              role="status"
              className="mb-4 rounded-md bg-muted px-3 py-2 text-sm text-muted-foreground"
            >
              Your session has expired. Please sign in again.
            </p>
          )}
          <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
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
              autoComplete="current-password"
              error={errors.password?.message}
              {...register("password")}
            />
            {submitError && (
              <p role="alert" className="text-sm text-destructive">
                {submitError}
              </p>
            )}
            <Button type="submit" isLoading={isSubmitting}>
              Sign in
            </Button>
            <p className="text-center text-sm text-muted-foreground">
              New to BELC Quiz Platform?{" "}
              <Link to="/register" className="font-medium text-primary hover:underline">
                Create an account
              </Link>
            </p>
          </form>
        </CardContent>
      </Card>
    </PageContainer>
  );
}
