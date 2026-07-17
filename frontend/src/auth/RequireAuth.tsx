import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useIsAuthenticated } from "@/auth/authStore";

/**
 * Route guard for the authenticated area. Unauthenticated visitors are sent
 * to the login page, remembering where they were headed so login can return
 * them there.
 */
export function RequireAuth() {
  const isAuthenticated = useIsAuthenticated();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  return <Outlet />;
}
