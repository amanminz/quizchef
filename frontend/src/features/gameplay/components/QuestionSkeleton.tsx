import { Card, CardContent } from "@/components/common/Card";

/** A loading placeholder shaped like QuestionCard, shown while the question is being fetched. */
export function QuestionSkeleton() {
  return (
    <Card>
      <CardContent className="flex flex-col gap-5 p-6">
        <div className="flex items-center justify-between">
          <div className="h-5 w-32 animate-pulse rounded-full bg-muted" />
          <div className="h-5 w-16 animate-pulse rounded-full bg-muted" />
        </div>
        <div className="h-1.5 w-full animate-pulse rounded-full bg-muted" />
        <div className="h-6 w-3/4 animate-pulse rounded bg-muted" />
        <div className="grid gap-2 sm:grid-cols-2">
          {[0, 1, 2, 3].map((index) => (
            <div key={index} className="h-12 animate-pulse rounded-md bg-muted" />
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
