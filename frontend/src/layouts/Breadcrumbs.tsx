import { ChevronRight } from "lucide-react";
import { Link, useLocation, useParams } from "react-router-dom";
import { useQuiz } from "@/features/quizzes/hooks/useQuiz";

interface Crumb {
  label: string;
  href?: string;
}

const STATIC_LABELS: Record<string, string> = {
  dashboard: "Dashboard",
  quizzes: "Quizzes",
  sessions: "Sessions",
  new: "New Quiz",
  questions: "Questions",
  review: "Review"
};

/**
 * Route-aware breadcrumb trail for the dashboard area. Lives in layouts/,
 * not components/navigation/, because it knows the app's actual routes
 * (quiz titles included) — the generic nav components stay route-agnostic.
 */
export function Breadcrumbs() {
  const location = useLocation();
  const params = useParams<{ quizId?: string }>();
  const { data: quiz } = useQuiz(params.quizId);

  const segments = location.pathname.split("/").filter(Boolean);
  if (segments.length <= 1) {
    return null;
  }

  const quizTitle =
    quiz?.localizations?.find((localization) => localization.languageCode === quiz.defaultLanguage)
      ?.title ?? quiz?.localizations?.[0]?.title;

  const crumbs: Crumb[] = [];
  let path = "";
  for (const segment of segments) {
    path += `/${segment}`;
    if (segment === params.quizId) {
      crumbs.push({ label: quizTitle ?? "Quiz", href: path });
    } else {
      crumbs.push({ label: STATIC_LABELS[segment] ?? segment, href: path });
    }
  }

  return (
    <nav aria-label="Breadcrumb" className="mb-4 flex flex-wrap items-center gap-1.5 text-sm">
      {crumbs.map((crumb, index) => {
        const isLast = index === crumbs.length - 1;
        return (
          <span key={crumb.href} className="flex items-center gap-1.5">
            {index > 0 && <ChevronRight aria-hidden className="h-3.5 w-3.5 text-muted-foreground" />}
            {isLast || !crumb.href ? (
              <span aria-current="page" className="font-medium text-foreground">
                {crumb.label}
              </span>
            ) : (
              <Link to={crumb.href} className="text-muted-foreground hover:text-foreground">
                {crumb.label}
              </Link>
            )}
          </span>
        );
      })}
    </nav>
  );
}
