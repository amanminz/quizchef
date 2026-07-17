import { ArrowLeft } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router-dom";

export interface WorkflowHeaderProps {
  title: string;
  backHref?: string;
  backLabel?: string;
  /** e.g. an EntityStatusBadge — rendered next to the title. */
  status?: ReactNode;
  actions?: ReactNode;
}

/**
 * The header for a page inside a multi-step workflow (quiz authoring
 * today) — a back link, the current entity's title and status, and
 * actions. Generic: it renders slots, never a domain concept. Pair with
 * ProgressStepper underneath to show where the page sits in the flow.
 */
export function WorkflowHeader({ title, backHref, backLabel, status, actions }: WorkflowHeaderProps) {
  return (
    <div className="mb-4 flex flex-wrap items-start justify-between gap-4">
      <div>
        {backHref && (
          <Link
            to={backHref}
            className="mb-2 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft aria-hidden className="h-4 w-4" />
            {backLabel ?? "Back"}
          </Link>
        )}
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
          {status}
        </div>
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  );
}
