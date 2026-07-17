import { NavLink } from "react-router-dom";
import { cn } from "@/utils/cn";

const links = [
  { to: "/dashboard", label: "Dashboard" },
  { to: "/quizzes", label: "Quizzes" },
  { to: "/sessions", label: "Sessions" }
];

export interface AppNavProps {
  orientation?: "horizontal" | "vertical";
}

/** The authenticated area's primary navigation — a sidebar on desktop, usable inline anywhere. */
export function AppNav({ orientation = "horizontal" }: AppNavProps) {
  return (
    <nav
      aria-label="Primary"
      className={cn("flex gap-1", orientation === "vertical" ? "flex-col" : "items-center")}
    >
      {links.map((link) => (
        <NavLink
          key={link.to}
          to={link.to}
          className={({ isActive }) =>
            cn(
              "rounded-md px-3 py-2 text-sm font-medium transition-colors",
              isActive
                ? "bg-secondary text-secondary-foreground"
                : "text-muted-foreground hover:bg-muted hover:text-foreground"
            )
          }
        >
          {link.label}
        </NavLink>
      ))}
    </nav>
  );
}
