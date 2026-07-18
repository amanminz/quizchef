import { NavLink } from "react-router-dom";
import { cn } from "@/utils/cn";

export interface NavLinkItem {
  to: string;
  label: string;
}

const DEFAULT_LINKS: NavLinkItem[] = [
  { to: "/dashboard", label: "Dashboard" },
  { to: "/profile", label: "Profile" }
];

export interface AppNavProps {
  orientation?: "horizontal" | "vertical";
  /**
   * The links to render. Generic by rule: this component knows nothing
   * about roles or features — a layout (which may be feature-aware,
   * RFC-009) computes the list. Navigation reflects permissions; the
   * routes themselves stay reachable and the backend still decides.
   */
  links?: NavLinkItem[];
}

/** The authenticated area's primary navigation — a sidebar on desktop, usable inline anywhere. */
export function AppNav({ orientation = "horizontal", links = DEFAULT_LINKS }: AppNavProps) {
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
