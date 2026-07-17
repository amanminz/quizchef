import type { ReactNode } from "react";

export interface SectionHeaderProps {
  title: string;
  description?: string;
  /** Right-aligned slot for actions (buttons, filters). */
  actions?: ReactNode;
}

export function SectionHeader({ title, description, actions }: SectionHeaderProps) {
  return (
    <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
        {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  );
}
