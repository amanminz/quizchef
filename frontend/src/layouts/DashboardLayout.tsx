import { Link, Outlet } from "react-router-dom";
import { useAuth } from "@/auth/useAuth";
import { useCurrentUser } from "@/auth/useCurrentUser";
import { Button } from "@/components/common/Button";
import { OfflineIndicator } from "@/components/feedback/OfflineIndicator";
import { AppNav } from "@/components/navigation/AppNav";

/** Shell for the authenticated area: navigation, identity, logout. */
export function DashboardLayout() {
  const { logout } = useAuth();
  const { data: currentUser } = useCurrentUser();

  return (
    <div className="flex min-h-screen flex-col">
      <OfflineIndicator />
      <header className="border-b">
        <div className="mx-auto flex h-14 w-full max-w-5xl items-center justify-between gap-4 px-4 sm:px-6">
          <div className="flex items-center gap-6">
            <Link to="/dashboard" className="text-lg font-bold tracking-tight">
              QuizChef
            </Link>
            <AppNav />
          </div>
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
        <Outlet />
      </main>
    </div>
  );
}
