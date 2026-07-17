import { useNavigate } from "react-router-dom";
import { Card, CardContent } from "@/components/common/Card";
import { PageContainer } from "@/components/common/PageContainer";
import { SectionHeader } from "@/components/common/SectionHeader";
import {
  JoinSessionForm,
  type JoinSessionFormValues
} from "@/features/gameplay/components/JoinSessionForm";
import { useJoinSession } from "@/features/gameplay/hooks/useJoinSession";

/** The public "join a game" entry: enter a PIN and a nickname, then play. */
export function PlayPage() {
  const navigate = useNavigate();
  const joinMutation = useJoinSession();

  const onSubmit = async (values: JoinSessionFormValues) => {
    await joinMutation.mutateAsync({
      pin: values.pin,
      request: { displayName: values.displayName, preferredLanguage: values.preferredLanguage }
    });
    navigate(`/play/${values.pin}`);
  };

  return (
    <PageContainer className="max-w-md py-16">
      <SectionHeader title="Join a game" description="Enter the code your host shared." />
      <Card>
        <CardContent className="pt-6">
          <JoinSessionForm
            onSubmit={onSubmit}
            isSubmitting={joinMutation.isPending}
            error={joinMutation.error}
          />
        </CardContent>
      </Card>
    </PageContainer>
  );
}
