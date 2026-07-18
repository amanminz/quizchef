import { BookOpen, Radio, UserRound } from "lucide-react";

/**
 * What each role means, in product terms — static copy mirroring the
 * backend's code-defined RolePermissions matrix (RFC-002). Display only:
 * the matrix itself lives server-side and is the authority.
 */
export function RoleExplanation() {
  return (
    <ul className="flex flex-col gap-3 text-sm">
      <li className="flex items-start gap-3">
        <UserRound aria-hidden className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
        <span>
          <span className="font-semibold">Member</span> — join and play live quizzes, manage your
          own account.
        </span>
      </li>
      <li className="flex items-start gap-3">
        <BookOpen aria-hidden className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
        <span>
          <span className="font-semibold">Host</span> — everything a member can, plus author
          quizzes and questions…
        </span>
      </li>
      <li className="flex items-start gap-3">
        <Radio aria-hidden className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" />
        <span>…and run live sessions: open lobbies, present questions, reveal answers, and crown
          winners.
        </span>
      </li>
    </ul>
  );
}
