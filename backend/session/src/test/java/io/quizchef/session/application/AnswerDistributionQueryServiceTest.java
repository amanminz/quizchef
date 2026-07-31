package io.quizchef.session.application;

import static io.quizchef.session.application.SessionOrchestrationTestFixtures.QUIZ_VERSION;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.host;
import static io.quizchef.session.application.SessionOrchestrationTestFixtures.sessionHostedBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantAnswer;
import io.quizchef.session.domain.QuestionTimer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.exception.AnswerDistributionNotAvailableException;
import io.quizchef.session.domain.exception.NotSessionHostException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnswerDistributionQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final ParticipantRepository participantRepository = mock(ParticipantRepository.class);
    private final GameplayQuizQuery gameplayQuizQuery = mock(GameplayQuizQuery.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final AnswerDistributionQueryService service = new AnswerDistributionQueryService(
            sessionRepository, participantRepository, gameplayQuizQuery, authorizationService);

    private final CurrentUser hostUser = host();
    private final UUID questionId = UUID.randomUUID();
    private final UUID optionA = UUID.randomUUID();
    private final UUID optionB = UUID.randomUUID();
    private final UUID optionC = UUID.randomUUID();

    private Session revealedSession() {
        Session session = sessionHostedBy(hostUser, "600001");
        session.openLobby();
        Participant seed = connectedParticipant(session);
        session.start();
        session.openQuestion(questionId, QuestionTimer.startingAt(NOW, Duration.ofSeconds(30)));
        session.closeQuestion();
        session.revealAnswer();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(participantRepository.findBySessionId(session.getId())).thenReturn(List.of(seed));
        when(gameplayQuizQuery.load(QUIZ_VERSION)).thenReturn(new PlayableQuizView(30, List.of(
                new PlayableQuizView.PlayableQuestion(questionId, Difficulty.EASY,
                        Set.of(optionA), Set.of(optionA, optionB, optionC)))));
        return session;
    }

    private Participant connectedParticipant(Session session) {
        Participant participant = Participant.guest(session.getId(),
                GuestParticipantToken.generate(), "Player " + UUID.randomUUID(), LanguageCode.of("en"));
        session.registerParticipant(participant.getId(), participant.key());
        participant.connect(NOW);
        return participant;
    }

    private static ParticipantAnswer answerSelecting(UUID questionId, UUID... optionIds) {
        return new ParticipantAnswer(questionId, Set.of(optionIds), LanguageCode.of("en"), NOW, 1_000L, 100);
    }

    @Test
    void countsEachOptionsAcceptedSelectionsAndTheNoAnswerCount() {
        Session session = revealedSession();
        Participant answeredA = connectedParticipant(session);
        answeredA.recordAnswer(answerSelecting(questionId, optionA));
        Participant answeredB = connectedParticipant(session);
        answeredB.recordAnswer(answerSelecting(questionId, optionB));
        Participant didNotAnswer = connectedParticipant(session);
        when(participantRepository.findBySessionId(session.getId()))
                .thenReturn(List.of(answeredA, answeredB, didNotAnswer));

        AnswerDistributionView view = service.distribution(hostUser, session.getId());

        assertThat(view.answeredCount()).isEqualTo(2);
        assertThat(view.eligibleParticipantCount()).isEqualTo(3);
        assertThat(view.noAnswerCount()).isEqualTo(1);
        assertThat(view.options()).extracting(
                        AnswerDistributionView.OptionCount::optionId,
                        AnswerDistributionView.OptionCount::count,
                        AnswerDistributionView.OptionCount::percentage)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(optionA, 1, 33),
                        org.assertj.core.groups.Tuple.tuple(optionB, 1, 33),
                        org.assertj.core.groups.Tuple.tuple(optionC, 0, 0));
    }

    @Test
    void aMultipleAnswerQuestionsSelectionsCanExceedTheAnsweredCount() {
        Session session = revealedSession();
        Participant answered = connectedParticipant(session);
        answered.recordAnswer(answerSelecting(questionId, optionA, optionB));
        when(participantRepository.findBySessionId(session.getId())).thenReturn(List.of(answered));

        AnswerDistributionView view = service.distribution(hostUser, session.getId());

        assertThat(view.answeredCount()).isEqualTo(1);
        int totalSelections = view.options().stream()
                .mapToInt(AnswerDistributionView.OptionCount::count).sum();
        assertThat(totalSelections).isEqualTo(2);
    }

    @Test
    void unavailableBeforeTheAnswerIsRevealed() {
        Session session = sessionHostedBy(hostUser, "600002");
        session.openLobby();
        connectedParticipant(session);
        session.start();
        session.openQuestion(questionId, QuestionTimer.startingAt(NOW, Duration.ofSeconds(30)));
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatExceptionOfType(AnswerDistributionNotAvailableException.class)
                .isThrownBy(() -> service.distribution(hostUser, session.getId()));

        session.closeQuestion();
        assertThatExceptionOfType(AnswerDistributionNotAvailableException.class)
                .isThrownBy(() -> service.distribution(hostUser, session.getId()));
    }

    @Test
    void onlyTheHostReadsDistribution() {
        Session session = revealedSession();

        assertThatExceptionOfType(NotSessionHostException.class)
                .isThrownBy(() -> service.distribution(host(), session.getId()));
    }
}
