import { Link } from "react-router-dom";
import { Button } from "@/components/common/Button";
import { PageContainer } from "@/components/common/PageContainer";

export function NotFoundPage() {
  return (
    <PageContainer className="flex flex-col items-center py-24 text-center">
      <p className="text-6xl font-bold text-muted-foreground">404</p>
      <h1 className="mt-4 text-2xl font-semibold">Page not found</h1>
      <p className="mt-2 text-sm text-muted-foreground">
        The page you are looking for does not exist or has moved.
      </p>
      <Link to="/" className="mt-8">
        <Button variant="secondary">Back to home</Button>
      </Link>
    </PageContainer>
  );
}
