import { WifiOff } from "lucide-react";
import { useOnlineStatus } from "@/hooks/useOnlineStatus";

/** A banner shown while the browser reports no network connectivity. */
export function OfflineIndicator() {
  const online = useOnlineStatus();

  if (online) {
    return null;
  }
  return (
    <div
      role="status"
      className="flex items-center justify-center gap-2 bg-destructive px-4 py-1.5 text-sm text-destructive-foreground"
    >
      <WifiOff aria-hidden className="h-4 w-4" />
      You are offline — changes cannot be saved until the connection returns.
    </div>
  );
}
