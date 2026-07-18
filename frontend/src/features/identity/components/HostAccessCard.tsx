import { Radio } from "lucide-react";
import { ErrorPanel } from "@/components/common/ErrorPanel";
import { Button } from "@/components/common/Button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/common/Card";
import { RoleExplanation } from "@/features/identity/components/RoleExplanation";

export interface HostAccessCardProps {
  onRequest: () => void;
  isRequesting: boolean;
  error?: unknown;
}

/** The onboarding CTA: what hosting means, and the one button that grants it. */
export function HostAccessCard({ onRequest, isRequesting, error }: HostAccessCardProps) {
  return (
    <Card>
      <CardHeader>
        <Radio aria-hidden className="mb-1 h-6 w-6 text-primary" />
        <CardTitle>Become a Host</CardTitle>
        <CardDescription>
          Hosting unlocks quiz authoring and live sessions. It takes one click — your account keeps
          everything it already has.
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <RoleExplanation />
        {error != null && <ErrorPanel title="Could not grant host access" error={error} />}
        <Button onClick={onRequest} isLoading={isRequesting} className="self-start">
          Become a Host
        </Button>
      </CardContent>
    </Card>
  );
}
