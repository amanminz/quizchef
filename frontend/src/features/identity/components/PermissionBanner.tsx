import { Lock } from "lucide-react";
import { Link } from "react-router-dom";
import { Button } from "@/components/common/Button";

/**
 * Shown where a capability exists but the caller's role doesn't cover it —
 * cosmetic guidance only (RFC-009: frontend authorization is display; the
 * backend would 403 regardless). Points at the supported path to the
 * capability instead of hiding that it exists.
 */
export function PermissionBanner({ message }: { message: string }) {
  return (
    <div className="mb-6 flex flex-wrap items-center justify-between gap-3 rounded-md border border-primary/30 bg-primary/5 px-4 py-3 text-sm">
      <span className="flex items-center gap-2">
        <Lock aria-hidden className="h-4 w-4 text-primary" />
        {message}
      </span>
      <Link to="/profile/host-access">
        <Button size="sm">Become a Host</Button>
      </Link>
    </div>
  );
}
