/** "Question 3 of 10" — the question's position in the quiz. */
export function QuestionNumberBadge({ number, total }: { number: number; total: number }) {
  return (
    <span className="inline-flex items-center rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium text-muted-foreground">
      Question {number} of {total}
    </span>
  );
}
