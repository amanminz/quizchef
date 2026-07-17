import { Component, type ErrorInfo, type ReactNode } from "react";
import { ErrorPanel } from "@/components/common/ErrorPanel";

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  error: unknown;
}

/**
 * The global render-error net. Anything a component throws during render
 * lands here instead of white-screening the app; "Try again" re-renders the
 * subtree after the user (or a navigation) has changed the offending state.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: unknown): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: unknown, info: ErrorInfo): void {
    console.error("Unhandled render error", error, info.componentStack);
  }

  render() {
    if (this.state.error !== null) {
      return (
        <div className="mx-auto max-w-xl p-8">
          <ErrorPanel
            title="The application hit an unexpected error"
            error={this.state.error}
            onRetry={() => this.setState({ error: null })}
          />
        </div>
      );
    }
    return this.props.children;
  }
}
