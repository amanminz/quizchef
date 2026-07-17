import { NavLink } from "react-router-dom";
import { cn } from "@/utils/cn";

const links = [
  { to: "/dashboard", label: "Dashboard" },
  { to: "/quizzes", label: "Quizzes" },
  { to: "/sessions", label: "Sessions" }
];

/** The authenticated area's primary navigation. */
export function AppNav() {
  return (
    <nav aria-label="Primary" className="flex items-center gap-1">
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
