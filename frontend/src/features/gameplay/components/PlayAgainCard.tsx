import { Link } from "react-router-dom";
import { Button } from "@/components/common/Button";

/**
 * The "what next" actions after completion, per role: a host can start
 * another session or return to their dashboard; a participant can jump
 * back to the PIN entry to play another quiz. Links only — nothing here
 * touches the finished session.
 */
export function PlayAgainCard({ role }: { role: "host" | "participant" }) {
  if (role === "host") {
    return (
      <div className="flex flex-wrap items-center gap-2">
        <Link to="/sessions/new">
          <Button size="sm">Host Another Session</Button>
        </Link>
        <Link to="/dashboard">
          <Button variant="secondary" size="sm">
            Return to Dashboard
          </Button>
        </Link>
      </div>
    );
  }
  return (
    <div className="flex flex-wrap items-center gap-2">
      <Link to="/play">
        <Button size="sm">Play Another Quiz</Button>
      </Link>
    </div>
  );
}
