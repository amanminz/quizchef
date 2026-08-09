package io.quizchef.session.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuizView.PlayableQuestion;
import io.quizchef.session.domain.Session;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Randomises the order a session will play its questions in. Host only.
 *
 * <p>The point is replays. A published quiz is immutable and a session pins
 * the version it executes, so the same quiz played twice runs the same
 * sequence — right for the content, wrong for the room, who remember what
 * came third. Shuffling belongs to the session for exactly that reason:
 * the quiz is never touched, so a past session's questions stay the
 * questions it actually asked, and two sessions of one quiz can differ.
 *
 * <p>The server draws the order, not the client. A caller-supplied
 * permutation would be a client deciding gameplay, which ADR-006 gives to
 * the server everywhere else — and it would let a host see the order before
 * committing to it, which is the one thing a shuffle should not offer.
 */
@Service
public class ShuffleQuestionsApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ShuffleQuestionsApplicationService.class);

    private final SessionRepository sessionRepository;
    private final GameplayQuizQuery gameplayQuizQuery;
    private final AuthorizationService authorizationService;
    private final Random random;

    @Autowired
    public ShuffleQuestionsApplicationService(SessionRepository sessionRepository,
                                              GameplayQuizQuery gameplayQuizQuery,
                                              AuthorizationService authorizationService) {
        this(sessionRepository, gameplayQuizQuery, authorizationService, new SecureRandom());
    }

    /** Seedable for tests, which need a shuffle they can predict. */
    ShuffleQuestionsApplicationService(SessionRepository sessionRepository,
                                       GameplayQuizQuery gameplayQuizQuery,
                                       AuthorizationService authorizationService,
                                       Random random) {
        this.sessionRepository = sessionRepository;
        this.gameplayQuizQuery = gameplayQuizQuery;
        this.authorizationService = authorizationService;
        this.random = random;
    }

    @Transactional
    public SessionSummaryView shuffle(CurrentUser currentUser, UUID sessionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        SessionHostPolicy.requireHost(currentUser, session);

        List<UUID> questionIds = gameplayQuizQuery.load(session.getPublishedQuizVersionId())
                .questions().stream()
                .map(PlayableQuestion::questionId)
                .toList();

        List<UUID> shuffled = new ArrayList<>(questionIds);
        Collections.shuffle(shuffled, random);

        // The aggregate decides whether the session may still be reordered
        // and that this is a permutation of the right set; this service only
        // decides which permutation.
        Set<UUID> quizQuestionIds = new LinkedHashSet<>(questionIds);
        session.useQuestionOrder(shuffled, quizQuestionIds);
        sessionRepository.saveAndFlush(session);

        log.info("Shuffled {} questions for session {}", shuffled.size(), sessionId);
        return SessionSummaryView.of(session);
    }
}
