import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { isApiClientError } from "@/api/apiError";
import { AuthProvider } from "@/auth/AuthProvider";
import { ErrorBoundary } from "@/components/feedback/ErrorBoundary";
import { RealtimeProvider } from "@/realtime/RealtimeProvider";
import { useApplyTheme } from "@/theme/useApplyTheme";

/**
 * The retry policy for all server state lives here, in one place: client
 * errors (4xx) never retry — the server said no and will keep saying no —
 * while network and server errors retry twice. axios adds no retries of its
 * own, so nothing is ever retried at two layers.
 */
function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: (failureCount, error) => {
          if (isApiClientError(error) && error.status !== null && error.status < 500) {
            return false;
          }
          return failureCount < 2;
        },
        staleTime: 30_000,
        refetchOnWindowFocus: false
      },
      mutations: { retry: false }
    }
  });
}

function ThemedChildren({ children }: { children: ReactNode }) {
  useApplyTheme();
  return children;
}

/**
 * The application's provider stack, outermost first: the error boundary
 * catches everything below it; queries are available to auth (login
 * invalidates them); realtime sits inside auth for future authenticated
 * connects.
 */
export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(createQueryClient);

  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <RealtimeProvider>
            <ThemedChildren>{children}</ThemedChildren>
          </RealtimeProvider>
        </AuthProvider>
      </QueryClientProvider>
    </ErrorBoundary>
  );
}
