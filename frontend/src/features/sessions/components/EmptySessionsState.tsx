import { Radio } from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "@/components/common/Button";
import { EmptyState } from "@/components/common/EmptyState";

/** No hosted sessions tracked in this browser yet. */
export function EmptySessionsState() {
  return (
    <EmptyState
      icon={Radio}
      title="No sessions yet"
      description="Create a session from a published quiz to host a live game. Sessions you host from this browser appear here."
      action={
        <Link to="/sessions/new">
          <Button>New Session</Button>
        </Link>
      }
    />
  );
}
