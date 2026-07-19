import { Link, Outlet } from "react-router-dom";
import { OfflineIndicator } from "@/components/feedback/OfflineIndicator";

/** Shell for unauthenticated pages: home, login, play. */
export function PublicLayout() {
  return (
    <div className="flex min-h-screen flex-col">
      <OfflineIndicator />
      <header className="border-b">
        <div className="mx-auto flex h-14 w-full max-w-5xl items-center px-4 sm:px-6">
          <Link to="/" className="text-lg font-bold tracking-tight">
            BELC Quiz Platform
          </Link>
        </div>
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  );
}
