package io.quizchef.session.application;

import io.quizchef.session.domain.Participant;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Undoes a question's attempt across the whole room: every accepted answer
 * discarded, every point it awarded taken back.
 *
 * <p>The shared heart of both live recoveries. A corrected question is
 * replayed and a removed question is skipped, and in either case the
 * attempt that already happened must leave nothing behind — section 8's
 * requirement that it be "as if the removed question never contributed".
 *
 * <p>The reversal is exact rather than approximate because it is arithmetic
 * on the answers themselves ({@link Participant#discardAnswerFor}), not a
 * recomputation from the quiz: whatever the scoring rule awarded is
 * precisely what comes back off. And it is deliberately the <em>only</em>
 * derived state touched — the leaderboard, the standings, the distribution,
 * the answer progress, and the personal result projections are all computed
 * from participants' answers at read time (ADR-006), so reversing the
 * answers reverses all of them at once, with nothing left to keep in step.
 *
 * <p>Runs inside the caller's transaction, under the session's write lock,
 * so an answer cannot arrive between the discard and the commit.
 */
@Component
class CancelQuestionAttempt {

    private static final Logger log = LoggerFactory.getLogger(CancelQuestionAttempt.class);

    private final ParticipantRepository participantRepository;

    CancelQuestionAttempt(ParticipantRepository participantRepository) {
        this.participantRepository = participantRepository;
    }

    /**
     * @return how many answers were cancelled — zero when nobody had
     *         answered yet, which is the ordinary case for a question the
     *         host catches early
     */
    int cancel(UUID sessionId, UUID questionId) {
        List<Participant> cancelled = participantRepository.findBySessionId(sessionId).stream()
                .filter(participant -> participant.discardAnswerFor(questionId))
                .toList();
        if (cancelled.isEmpty()) {
            return 0;
        }
        participantRepository.saveAll(cancelled);
        log.info("Cancelled {} answers for question {} in session {}",
                cancelled.size(), questionId, sessionId);
        return cancelled.size();
    }
}
