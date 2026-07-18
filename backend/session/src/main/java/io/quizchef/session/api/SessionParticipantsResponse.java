package io.quizchef.session.api;

import io.quizchef.session.application.SessionRosterMemberView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * The host's roster projection: every joined participant by display name,
 * in stable join order — what the projected lobby wall renders.
 */
public record SessionParticipantsResponse(
        UUID sessionId,
        int participantCount,
        List<SessionParticipantDto> participants
) {

    public record SessionParticipantDto(
            UUID participantId,
            @Schema(example = "Amelia") String displayName,
            boolean connected
    ) {
    }

    static SessionParticipantsResponse from(UUID sessionId, List<SessionRosterMemberView> roster) {
        return new SessionParticipantsResponse(
                sessionId,
                roster.size(),
                roster.stream()
                        .map(member -> new SessionParticipantDto(
                                member.participantId(), member.displayName(), member.connected()))
                        .toList());
    }
}
