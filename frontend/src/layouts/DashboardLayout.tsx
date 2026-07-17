import { Menu } from "lucide-react";
import { useState } from "react";
import { Link, Outlet } from "react-router-dom";
import { useAuth } from "@/auth/useAuth";
import { useCurrentUser } from "@/auth/useCurrentUser";
import { Button } from "@/components/common/Button";
import { OfflineIndicator } from "@/components/feedback/OfflineIndicator";
import { AppNav } from "@/components/navigation/AppNav";
import { Breadcrumbs } from "@/layouts/Breadcrumbs";
import { cn } from "@/utils/cn";

/** Shell for the authenticated area: sidebar nav, top bar (user + logout), breadcrumbs. */
export function DashboardLayout() {
  const { logout } = useAuth();
  const { data: currentUser } = useCurrentUser();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div className="flex min-h-screen flex-col">
      <OfflineIndicator />
      <div className="flex flex-1">
        <aside
          className={cn(
            "fixed inset-y-0 left-0 z-20 w-56 border-r bg-background p-4 transition-transform sm:static sm:translate-x-0",
            sidebarOpen ? "translate-x-0" : "-translate-x-full"
          )}
        >
          <Link
            to="/dashboard"
            className="mb-6 block text-lg font-bold tracking-tight"
            onClick={() => setSidebarOpen(false)}
          >
            QuizChef
          </Link>
          <AppNav orientation="vertical" />
        </aside>

        {sidebarOpen && (
          <button
            type="button"
            aria-label="Close menu"
            className="fixed inset-0 z-10 bg-black/40 sm:hidden"
            onClick={() => setSidebarOpen(false)}
          />
        )}

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="border-b">
            <div className="flex h-14 items-center justify-between gap-4 px-4 sm:px-6">
              <Button
                variant="ghost"
                size="sm"
                className="sm:hidden"
                aria-label="Open menu"
                onClick={() => setSidebarOpen(true)}
              >
                <Menu aria-hidden className="h-5 w-5" />
              </Button>
              <span className="hidden sm:block" />
              <div className="flex items-center gap-3">
                {currentUser && (
                  <span className="hidden text-sm text-muted-foreground sm:inline">
                    {currentUser.roles?.join(", ")}
                  </span>
                )}
                <Button variant="ghost" size="sm" onClick={logout}>
                  Log out
                </Button>
              </div>
            </div>
          </header>
          <main className="flex-1">
            <div className="mx-auto w-full max-w-5xl px-4 pt-4 sm:px-6">
              <Breadcrumbs />
            </div>
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  );
}
