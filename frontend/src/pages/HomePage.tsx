import { Link } from "react-router-dom";
import { Button } from "@/components/common/Button";
import { PageContainer } from "@/components/common/PageContainer";

export function HomePage() {
  return (
    <PageContainer className="flex flex-col items-center py-24 text-center">
      <h1 className="text-5xl font-bold tracking-tight">BELC Family Quiz Platform</h1>
      <p className="mt-4 max-w-md text-lg text-muted-foreground">
        Live Bible quizzes for your church — host a game, share a PIN, and play together.
      </p>
      <div className="mt-8 flex gap-3">
        <Link to="/play">
          <Button size="lg">Join a game</Button>
        </Link>
        <Link to="/login">
          <Button size="lg" variant="secondary">
            Host sign in
          </Button>
        </Link>
      </div>
    </PageContainer>
  );
}
