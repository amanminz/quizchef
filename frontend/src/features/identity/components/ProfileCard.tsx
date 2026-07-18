import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/common/Card";
import { RoleBadge } from "@/features/identity/components/RoleBadge";
import type { PlatformRole } from "@/types/api";

export interface ProfileCardProps {
  displayName?: string;
  email?: string;
  identityType?: string;
  roles: PlatformRole[];
}

/** The caller's own account, as the server describes it. */
export function ProfileCard({ displayName, email, identityType, roles }: ProfileCardProps) {
  return (
    <Card>
      <CardHeader>
        <div className="flex items-start justify-between gap-2">
          <CardTitle>{displayName ?? "Your account"}</CardTitle>
          <span className="flex flex-wrap gap-1.5">
            {roles.map((role) => (
              <RoleBadge key={role} role={role} />
            ))}
          </span>
        </div>
        {email && <CardDescription>{email}</CardDescription>}
      </CardHeader>
      <CardContent className="text-sm text-muted-foreground">
        <p>Account type: {identityType === "REGISTERED" ? "Registered" : identityType}</p>
      </CardContent>
    </Card>
  );
}
