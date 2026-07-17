import { EntityStatusBadge } from "@/components/common/EntityStatusBadge";
import { sessionStateLabel, sessionStateTone } from "@/features/sessions/statusTone";
import type { SessionState } from "@/features/sessions/types";

/** The session lifecycle pill — the host-facing label over the wire state. */
export function SessionStatusBadge({ state }: { state: SessionState | undefined }) {
  return <EntityStatusBadge label={sessionStateLabel(state)} tone={sessionStateTone(state)} />;
}
