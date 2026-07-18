package io.quizchef.session.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionRosterEntry;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The host's roster read — every joined participant's display name and
 * connection state, in stable join order. Closes RFC-004's "no roster
 * read endpoint yet" gap for the projected lobby wall: realtime join
 * events carry only participant ids (names never ride broadcasts), so the
 * wall re-reads this projection on each roster event instead.
 *
 * <p>Host-only: names across the whole roster are the host's projection —
 * a participant device has no reason to enumerate the room. Join order is
 * the display order; realtime updates must never reshuffle a projected
 * wall.
 */
@Service
public class SessionRosterQueryService {

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final AuthorizationService authorizationService;

    public SessionRosterQueryService(SessionRepository sessionRepository,
                                     ParticipantRepository participantRepository,
                                     AuthorizationService authorizationService) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<SessionRosterMemberView> roster(CurrentUser currentUser, UUID sessionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        SessionHostPolicy.requireHost(currentUser, session);

        Map<UUID, Participant> byId = participantRepository.findBySessionId(sessionId).stream()
                .collect(Collectors.toMap(Participant::getId, Function.identity()));
        return session.roster().stream()
                .sorted(Comparator.comparingInt(SessionRosterEntry::joinOrder))
                .map(entry -> byId.get(entry.participantId()))
                .filter(participant -> participant != null)
                .map(participant -> new SessionRosterMemberView(
                        participant.getId(),
                        participant.getDisplayName(),
                        participant.isConnected()))
                .toList();
    }
}
