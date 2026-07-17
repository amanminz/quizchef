import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, useLocation, useRoutes } from "react-router-dom";
import { routes } from "@/app/Router";
import { AuthProvider } from "@/auth/AuthProvider";
import { RealtimeProvider } from "@/realtime/RealtimeProvider";

function TestRoutes() {
  return useRoutes(routes);
}

/** Exposes the current pathname so tests can assert where routing landed. */
function LocationProbe() {
  const location = useLocation();
  return (
    <span data-testid="location" hidden>
      {location.pathname}
    </span>
  );
}

/**
 * Mounts the real route tree inside the real provider stack. Tests use the
 * declarative MemoryRouter (same tree, no data-router Request machinery,
 * which clashes with MSW under jsdom); the app itself runs the data router.
 * Retries are off so failure paths assert immediately.
 */
export function renderApp(initialRoute = "/") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } }
  });

  const view = render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RealtimeProvider>
          <MemoryRouter initialEntries={[initialRoute]}>
            <TestRoutes />
            <LocationProbe />
          </MemoryRouter>
        </RealtimeProvider>
      </AuthProvider>
    </QueryClientProvider>
  );

  return { ...view, queryClient };
}

/** The pathname the router is currently showing. */
export function currentPath(): string {
  return screen.getByTestId("location").textContent ?? "";
}
