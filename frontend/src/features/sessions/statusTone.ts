import type { EntityStatusTone } from "@/components/common/EntityStatusBadge";
import type { SessionState } from "@/features/sessions/types";

/**
 * Maps the session lifecycle (RFC-004) to the host-facing vocabulary and a
 * badge tone. The wire states are the backend's; the labels are what a host
 * thinks in: a CREATED session is scheduled but not yet joinable, LOBBY is
 * waiting for participants, IN_PROGRESS is live.
 */
export function sessionStateLabel(state: SessionState | undefined): string {
  switch (state) {
    case "CREATED":
      return "Scheduled";
    case "LOBBY":
      return "Waiting";
    case "IN_PROGRESS":
      return "In Progress";
    case "FINISHED":
      return "Completed";
    case "ARCHIVED":
      return "Archived";
    default:
      return "Unknown";
  }
}

export function sessionStateTone(state: SessionState | undefined): EntityStatusTone {
  switch (state) {
    case "CREATED":
      return "neutral";
    case "LOBBY":
      return "warning";
    case "IN_PROGRESS":
      return "positive";
    case "FINISHED":
    case "ARCHIVED":
    default:
      return "neutral";
  }
}
