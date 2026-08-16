package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.session.domain.FinalStanding;
import io.quizchef.session.domain.LeaderboardService;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.event.SessionFinishedEvent;
import io.quizchef.session.infrastructure.persistence.FinalStandingRepository;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ends a session: captures its standings into durable history, flips the
 * aggregate to FINISHED, and announces it.
 *
 * <p>There are two ways a quiz runs out of questions — the host advances
 * past the last one, or the host removes the last one still to play — and
 * both must end the session identically. Keeping the ending in one place is
 * what guarantees that: history is written exactly once, by exactly this
 * code, whichever route got here. The caller persists the session.
 */
@Component
class SessionFinisher {

    private static final Logger log = LoggerFactory.getLogger(SessionFinisher.class);

    private final ParticipantRepository participantRepository;
    private final FinalStandingRepository finalStandingRepository;
    private final LeaderboardService leaderboardService;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    SessionFinisher(ParticipantRepository participantRepository,
                    FinalStandingRepository finalStandingRepository,
                    LeaderboardService leaderboardService,
                    DomainEventPublisher eventPublisher,
                    Clock clock) {
        this.participantRepository = participantRepository;
        this.finalStandingRepository = finalStandingRepository;
        this.leaderboardService = leaderboardService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    void finish(Session session, String because) {
        // Capture the standings *before* finishing, while the ranking is
        // still the one this game actually produced.
        captureFinalStandings(session);
        session.finish();
        eventPublisher.publish(new SessionFinishedEvent(session.getId(), clock.instant()));
        log.info("Session {} finished {}", session.getId(), because);
    }

    /**
     * Copies the finishing order into durable history: name, rank, and score
     * as they stood at this moment.
     *
     * <p>The rank is stored rather than recomputed later on purpose. The
     * host's live standings are projected and ranked at read time (ADR-006),
     * which is right for a running game and wrong for a finished one — a
     * change to the ranking rule would rewrite the result of an event that
     * already happened, and a display name edited afterwards would
     * retroactively rename someone in a past quiz.
     *
     * <p>Scores arrive here already correct for any question the host
     * removed or replayed: cancelling an attempt reverses its points on the
     * participants themselves, so history inherits the reversal rather than
     * having to know about it.
     *
     * <p>Idempotent by guard as well as by construction: finishing a
     * finished session already throws, but a snapshot that exists is never
     * written twice.
     */
    private void captureFinalStandings(Session session) {
        if (finalStandingRepository.existsBySessionId(session.getId())) {
            return;
        }
        Instant capturedAt = clock.instant();
        List<FinalStanding> standings = leaderboardService
                .rank(participantRepository.findBySessionId(session.getId()), session.roster())
                .stream()
                .map(entry -> FinalStanding.capture(session.getId(), entry, capturedAt))
                .toList();
        finalStandingRepository.saveAll(standings);
        log.info("Captured {} final standings for session {}", standings.size(), session.getId());
    }
}
