import { Menu } from "lucide-react";
import { useState } from "react";
import { Link, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "@/auth/useAuth";
import { useCurrentUser } from "@/auth/useCurrentUser";
import { Button } from "@/components/common/Button";
import { OfflineIndicator } from "@/components/feedback/OfflineIndicator";
import { AppNav, type NavLinkItem } from "@/components/navigation/AppNav";
import { usePermissions } from "@/features/identity/hooks/usePermissions";
import { usePresentationStore } from "@/features/sessions/presentationStore";
import { Breadcrumbs } from "@/layouts/Breadcrumbs";
import { cn } from "@/utils/cn";

/** The host-facing live-session screens presentation mode may take over. */
const PRESENTATION_ROUTES = /^\/sessions\/[^/]+\/(lobby|play)$/;

/**
 * Shell for the authenticated area: sidebar nav, top bar (user + logout),
 * breadcrumbs. The nav reflects permissions (layouts may be feature-aware,
 * RFC-009; the generic AppNav is not): authoring and hosting links appear
 * with the corresponding permissions. Cosmetic only — the routes stay
 * reachable and the backend still 403s where it must.
 */
export function DashboardLayout() {
  const { logout } = useAuth();
  const { data: currentUser } = useCurrentUser();
  const { hasPermission } = usePermissions();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const location = useLocation();
  const presentationActive = usePresentationStore((state) => state.active);
  // Presentation strips the chrome only on live-session screens — leaving
  // the session route always restores normal navigation, whatever the
  // stored flag says (cleanup on route change without losing the flag for
  // a same-session return).
  const presenting = presentationActive && PRESENTATION_ROUTES.test(location.pathname);

  const navLinks: NavLinkItem[] = [
    { to: "/dashboard", label: "Dashboard" },
    ...(hasPermission("QUIZ_CREATE")
      ? [
          { to: "/quizzes", label: "Quizzes" },
          { to: "/questions", label: "Question Library" }
        ]
      : []),
    ...(hasPermission("QUIZ_HOST") ? [{ to: "/sessions", label: "Sessions" }] : []),
    { to: "/profile", label: "Profile" }
  ];

  return (
    <div className="flex min-h-screen flex-col">
      <OfflineIndicator />
      <div className="flex flex-1">
        {/* Presentation hides the chrome with CSS on a stable tree — a
            branch swap would remount the page and bounce its realtime
            connection mid-event. */}
        <aside
          hidden={presenting}
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
          <AppNav orientation="vertical" links={navLinks} />
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
          <header hidden={presenting} className="border-b">
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
            <div hidden={presenting} className="mx-auto w-full max-w-5xl px-4 pt-4 sm:px-6">
              <Breadcrumbs />
            </div>
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  );
}
