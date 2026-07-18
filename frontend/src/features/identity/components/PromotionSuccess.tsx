import { PartyPopper } from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "@/components/common/Button";

/** Host access granted — celebrate and point at the two things a host does. */
export function PromotionSuccess() {
  return (
    <div
      role="status"
      className="flex flex-col items-center gap-4 rounded-lg border border-success/30 bg-success/5 px-6 py-12 text-center"
    >
      <PartyPopper aria-hidden className="h-8 w-8 text-success" />
      <div>
        <h2 className="text-xl font-bold">You're a host!</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Author your first quiz, or jump straight to hosting a session.
        </p>
      </div>
      <div className="flex flex-wrap items-center justify-center gap-2">
        <Link to="/quizzes/new">
          <Button size="sm">Create a Quiz</Button>
        </Link>
        <Link to="/sessions/new">
          <Button variant="secondary" size="sm">
            Host a Session
          </Button>
        </Link>
      </div>
    </div>
  );
}
