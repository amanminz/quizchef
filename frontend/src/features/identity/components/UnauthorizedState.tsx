import { ShieldAlert } from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "@/components/common/Button";
import { EmptyState } from "@/components/common/EmptyState";

/**
 * The server said 403 — rendered as a real outcome, not an impossibility
 * (frontend authorization is cosmetic; the backend's verdict is the one
 * that counts). Offers the supported path forward.
 */
export function UnauthorizedState({ description }: { description?: string }) {
  return (
    <EmptyState
      icon={ShieldAlert}
      title="You don't have access to this yet"
      description={
        description ?? "This capability needs the Host role. Becoming a host takes one click."
      }
      action={
        <Link to="/profile/host-access">
          <Button>Become a Host</Button>
        </Link>
      }
    />
  );
}
