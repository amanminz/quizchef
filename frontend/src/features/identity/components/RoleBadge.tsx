import { EntityStatusBadge, type EntityStatusTone } from "@/components/common/EntityStatusBadge";
import type { PlatformRole } from "@/types/api";

const labels: Record<PlatformRole, string> = {
  USER: "Member",
  QUIZ_MASTER: "Host",
  ADMIN: "Admin"
};

const tones: Record<PlatformRole, EntityStatusTone> = {
  USER: "neutral",
  QUIZ_MASTER: "positive",
  ADMIN: "warning"
};

/** One durable role as a pill — product-facing labels over the wire enums. */
export function RoleBadge({ role }: { role: PlatformRole }) {
  return <EntityStatusBadge label={labels[role]} tone={tones[role]} />;
}
