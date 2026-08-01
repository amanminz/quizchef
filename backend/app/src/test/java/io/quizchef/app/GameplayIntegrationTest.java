package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.Option;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.QuestionLocalization;
import io.quizchef.quiz.domain.QuestionType;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.QuizLocalization;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.websocket.api.ProtocolMessage;
import io.quizchef.websocket.api.ProtocolMessageType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The complete server-authoritative game end to end: create → lobby → join →
 * connect → start → preview → open → answer → close → reveal → leaderboard →
 * advance → … → finish. Verifies that the server computes every score, the
 * leaderboard ranks correctly, reconnection restores active gameplay, and
 * every step projects onto the realtime protocol (broker template mocked).
 *
 * <p>The reading period is overridden to 1 second for this class — the
 * {@code openQuestion}/{@code advanceToNext} helpers wait for the real,
 * server-scheduled preview-to-open transition to fire (never a fake clock:
 * this is exactly the production scheduling path, just given a short fuse)
 * so every existing test in this file reaches {@code QUESTION_OPEN} exactly
 * as before without needing to know the preview step happened.
 */
@SpringBootTest(properties = "quizchef.gameplay.question-preview-seconds=1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class GameplayIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Test
    void fullGame() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithTwoQuestions(host.reference());

        String sessionId = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());

        // Question 1: one guest right, one wrong
        String q1 = openQuestion(hostToken, sessionId);
        assertThat(q1).isEqualTo(quiz.questions().get(0).questionId().toString());
        answer(sessionId, guestA, q1, quiz.questions().get(0).correctOptionId());
        answer(sessionId, guestB, q1, quiz.questions().get(0).wrongOptionId());
        close(hostToken, sessionId);
        reveal(hostToken, sessionId);
        JsonNode board1 = leaderboard(hostToken, sessionId);
        // the correct guest leads with a server-computed score
        assertThat(board1.get("entries").get(0).get("participantId").asText()).isEqualTo(guestA);
        assertThat(board1.get("entries").get(0).get("score").asInt()).isGreaterThan(0);
        assertThat(board1.get("entries").get(1).get("score").asInt()).isZero();

        // reconnect mid-game restores gameplay: advance to Q2, then reconnect guestB
        String q2 = advanceToNext(hostToken, sessionId);
        assertThat(q2).isEqualTo(quiz.questions().get(1).questionId().toString());
        mockMvc.perform(post("/api/v1/sessions/reconnect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"guestParticipantToken": "%s"}
                                """.formatted(guestBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPhase").value("QUESTION_OPEN"))
                .andExpect(jsonPath("$.currentQuestionId").value(q2))
                .andExpect(jsonPath("$.remainingMillis").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.leaderboard").isArray());

        // finish the game
        answer(sessionId, guestA, q2, quiz.questions().get(1).correctOptionId());
        close(hostToken, sessionId);
        reveal(hostToken, sessionId);
        leaderboard(hostToken, sessionId);
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/advance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FINISHED"))
                .andExpect(jsonPath("$.currentPhase").doesNotExist());

        // persistence: guestA answered both questions and out-scores guestB
        UUID guestAId = UUID.fromString(guestA);
        UUID guestBId = UUID.fromString(guestB);
        var participants = participantRepository.findBySessionId(UUID.fromString(sessionId));
        int scoreA = participants.stream().filter(p -> p.getId().equals(guestAId))
                .findFirst().orElseThrow().getTotalScore();
        int scoreB = participants.stream().filter(p -> p.getId().equals(guestBId))
                .findFirst().orElseThrow().getTotalScore();
        assertThat(scoreA).isGreaterThan(scoreB);

        // realtime: the gameplay projections reached the session topic, and the
        // answer acknowledgements went to participant topics only
        var captor = org.mockito.ArgumentCaptor.forClass(ProtocolMessage.class);
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(startsWith("/topic/session/"), captor.capture());
        assertThat(captor.getAllValues()).extracting(ProtocolMessage::type)
                .contains(ProtocolMessageType.QUESTION_PREVIEW_STARTED, ProtocolMessageType.QUESTION_STARTED,
                        ProtocolMessageType.QUESTION_CLOSED, ProtocolMessageType.ANSWER_REVEALED,
                        ProtocolMessageType.LEADERBOARD_UPDATED, ProtocolMessageType.SESSION_FINISHED);
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(startsWith("/topic/participant/"), any(ProtocolMessage.class));
    }

    @Test
    void questionPreviewShowsThePromptWithoutOptionsThenOpensAutomatically() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithTwoQuestions(host.reference());
        String sessionId = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());

        String questionId = readJson(mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/start")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPhase").value("QUESTION_PREVIEW"))
                .andReturn().getResponse().getContentAsString()).get("currentQuestionId").asText();

        // The prompt is visible — options are genuinely absent, not merely
        // unrendered by a client — and a submission is rejected outright.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/questions/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("QUESTION_PREVIEW"))
                .andExpect(jsonPath("$.localizations[0].prompt").value("Prompt 1"))
                .andExpect(jsonPath("$.options").isEmpty())
                .andExpect(jsonPath("$.localizations[0].optionTexts").isEmpty())
                .andExpect(jsonPath("$.endsAt").exists())
                .andExpect(jsonPath("$.remainingMillis").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"participantId": "%s", "questionId": "%s", "selectedOptionIds": ["%s"]}
                                """.formatted(guestA, questionId, quiz.questions().get(0).correctOptionId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.answer.not-accepted"));

        // The host cannot skip ahead during preview either — every other
        // command still requires the phase it always required.
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.invalid-transition"));

        // No host action ends the preview — only the server's own timer.
        awaitQuestionOpen(sessionId);
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/questions/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("QUESTION_OPEN"))
                .andExpect(jsonPath("$.options[0].optionId").exists())
                .andExpect(jsonPath("$.localizations[0].optionTexts[0].text").value("True"))
                // The full 30-second answer duration — never shortened by the
                // 1-second preview that already elapsed.
                .andExpect(jsonPath("$.remainingMillis")
                        .value(org.hamcrest.Matchers.greaterThan(25_000)));

        var captor = org.mockito.ArgumentCaptor.forClass(ProtocolMessage.class);
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(startsWith("/topic/session/"), captor.capture());
        assertThat(captor.getAllValues()).extracting(ProtocolMessage::type)
                .contains(ProtocolMessageType.QUESTION_PREVIEW_STARTED, ProtocolMessageType.QUESTION_STARTED);
    }

    @Test
    void currentQuestionContentIsPublicAndPhaseAware() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithTwoQuestions(host.reference());
        String sessionId = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());

        // Before the first question opens there is nothing to serve.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/questions/current"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.no-current-question"));

        // Open: anonymous read gets content and clock — never correctness,
        // never the explanation (it routinely gives the answer away).
        openQuestion(hostToken, sessionId);
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/questions/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("QUESTION_OPEN"))
                .andExpect(jsonPath("$.questionNumber").value(1))
                .andExpect(jsonPath("$.totalQuestions").value(2))
                .andExpect(jsonPath("$.remainingMillis")
                        .value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.localizations[0].prompt").value("Prompt 1"))
                .andExpect(jsonPath("$.localizations[0].optionTexts[0].text").value("True"))
                .andExpect(jsonPath("$.localizations[0].explanation").doesNotExist())
                .andExpect(jsonPath("$.options[0].correct").doesNotExist())
                .andExpect(jsonPath("$.correctOptionIds").doesNotExist());

        // Closed: clock stopped, correctness still withheld.
        close(hostToken, sessionId);
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/questions/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("QUESTION_CLOSED"))
                .andExpect(jsonPath("$.remainingMillis").value(0))
                .andExpect(jsonPath("$.correctOptionIds").doesNotExist());

        // Revealed: correctness and the explanation now cross the wire.
        reveal(hostToken, sessionId);
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/questions/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correctOptionIds[0]")
                        .value(quiz.questions().get(0).correctOptionId().toString()))
                .andExpect(jsonPath("$.localizations[0].explanation").value("Because of 1"));

        // The next question starts the cycle clean.
        leaderboard(hostToken, sessionId);
        advanceToNext(hostToken, sessionId);
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/questions/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionNumber").value(2))
                .andExpect(jsonPath("$.localizations[0].prompt").value("Prompt 2"))
                .andExpect(jsonPath("$.correctOptionIds").doesNotExist());
    }

    @Test
    void resultsAreRoleScopedAndPhaseGated() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithTwoQuestions(host.reference());
        String sessionId = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());

        // While the question is open, standings would leak who answered
        // correctly before the reveal — withheld from everyone.
        String q1 = openQuestion(hostToken, sessionId);
        answer(sessionId, guestA, q1, quiz.questions().get(0).correctOptionId());
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/results")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.results.not-available"));
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/" + guestA + "/result"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.results.not-available"));

        // Revealed: the HOST recovers the full standings, names included.
        close(hostToken, sessionId);
        reveal(hostToken, sessionId);
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/results")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.currentPhase").value("ANSWER_REVEALED"))
                .andExpect(jsonPath("$.totalQuestions").value(2))
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.entries[0].participantId").value(guestA))
                .andExpect(jsonPath("$.entries[0].displayName").value("Ann"))
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.entries[0].score")
                        .value(org.hamcrest.Matchers.greaterThan(0)));

        // A participant device holds no host token: the full standings are
        // refused, and the personal read returns exactly one row — the
        // caller's own — with the framing counts but no other name.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/results"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/" + guestA + "/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantId").value(guestA))
                .andExpect(jsonPath("$.displayName").value("Ann"))
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.score").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.totalQuestions").value(2))
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.entries").doesNotExist());
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/" + guestB + "/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantId").value(guestB))
                .andExpect(jsonPath("$.rank").value(2))
                .andExpect(jsonPath("$.score").value(0));

        // A guessed participant id resolves to nothing.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/"
                        + UUID.randomUUID() + "/result"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("session.participant.not-found"));

        // Finish the game: the host's full standings stay readable forever
        // after (the ceremony reads them immediately); the participant's own
        // final rank is now held until the host explicitly releases it (see
        // finalResultsAreHeldUntilTheHostExplicitlyReleasesThem).
        leaderboard(hostToken, sessionId);
        advanceToNext(hostToken, sessionId);
        close(hostToken, sessionId);
        reveal(hostToken, sessionId);
        leaderboard(hostToken, sessionId);
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/advance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("FINISHED"));
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/results")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FINISHED"))
                .andExpect(jsonPath("$.currentPhase").doesNotExist())
                .andExpect(jsonPath("$.entries[0].displayName").value("Ann"));
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/" + guestA + "/result"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.results.not-available"));

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/results/release")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/" + guestA + "/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FINISHED"))
                .andExpect(jsonPath("$.rank").value(1));
    }

    @Test
    void finalResultsAreHeldUntilTheHostExplicitlyReleasesThem() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithTwoQuestions(host.reference());
        String sessionId = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());

        String q1 = openQuestion(hostToken, sessionId);
        answer(sessionId, guestA, q1, quiz.questions().get(0).correctOptionId());
        answer(sessionId, guestB, q1, quiz.questions().get(0).wrongOptionId());
        close(hostToken, sessionId);
        reveal(hostToken, sessionId);
        leaderboard(hostToken, sessionId);
        String q2 = advanceToNext(hostToken, sessionId);
        answer(sessionId, guestA, q2, quiz.questions().get(1).correctOptionId());
        close(hostToken, sessionId);
        reveal(hostToken, sessionId);
        leaderboard(hostToken, sessionId);
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/advance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("FINISHED"))
                .andExpect(jsonPath("$.finalResultsReleased").value(false));

        // The host runs the ceremony off the full standings immediately —
        // unaffected by the hold.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/results")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FINISHED"))
                .andExpect(jsonPath("$.entries[0].displayName").value("Ann"));

        // Participants see nothing about their final rank while pending.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/" + guestA + "/result"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.results.not-available"));

        // Only the host may release, and only once the session has finished.
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/results/release"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/results/release")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalResultsReleased").value(true));

        // Now every participant may read their own final rank.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/" + guestA + "/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(1));
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/" + guestB + "/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(2));

        // A duplicate release is harmless.
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/results/release")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalResultsReleased").value(true));
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/" + guestA + "/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(1));

        var captor = org.mockito.ArgumentCaptor.forClass(ProtocolMessage.class);
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(startsWith("/topic/session/"), captor.capture());
        assertThat(captor.getAllValues()).extracting(ProtocolMessage::type)
                .contains(ProtocolMessageType.FINAL_RESULTS_REVEALED);
    }

    @Test
    void answerDistributionCountsAcceptedAnswersAndIsHostOnly() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithTwoQuestions(host.reference());
        String sessionId = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());

        String q1 = openQuestion(hostToken, sessionId);
        answer(sessionId, guestA, q1, quiz.questions().get(0).correctOptionId());
        answer(sessionId, guestB, q1, quiz.questions().get(0).wrongOptionId());

        // Unavailable before the reveal.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/answer-distribution")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.distribution.not-available"));

        close(hostToken, sessionId);
        reveal(hostToken, sessionId);

        // Host-only.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/answer-distribution"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/answer-distribution")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answeredCount").value(2))
                .andExpect(jsonPath("$.eligibleParticipantCount").value(2))
                .andExpect(jsonPath("$.noAnswerCount").value(0))
                .andExpect(jsonPath("$.options[?(@.optionId=='"
                        + quiz.questions().get(0).correctOptionId() + "')].count").value(1))
                .andExpect(jsonPath("$.options[?(@.optionId=='"
                        + quiz.questions().get(0).wrongOptionId() + "')].count").value(1));
    }

    @Test
    void rankContextShowsNeighboursExceptOnTheFinalQuestion() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithTwoQuestions(host.reference());
        String sessionId = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());

        // Question 1 is not the quiz's last question: neighbours are shown.
        String q1 = openQuestion(hostToken, sessionId);
        answer(sessionId, guestA, q1, quiz.questions().get(0).correctOptionId());
        answer(sessionId, guestB, q1, quiz.questions().get(0).wrongOptionId());
        close(hostToken, sessionId);
        reveal(hostToken, sessionId);

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/" + guestB + "/rank-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(2))
                .andExpect(jsonPath("$.ahead.displayName").value("Ann"))
                .andExpect(jsonPath("$.behind").doesNotExist());

        // Question 2 is the quiz's last: neighbours are never disclosed,
        // even once its own answer is revealed.
        leaderboard(hostToken, sessionId);
        String q2 = advanceToNext(hostToken, sessionId);
        answer(sessionId, guestA, q2, quiz.questions().get(1).correctOptionId());
        close(hostToken, sessionId);
        reveal(hostToken, sessionId);

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/" + guestB + "/rank-context"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.rank-context.not-available"));
    }

    @Test
    void rosterReadIsHostOnlyAndInJoinOrder() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithTwoQuestions(host.reference());
        String sessionId = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());

        // The projected lobby wall needs names; the anonymous room does not.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.participants[0].participantId").value(guestA))
                .andExpect(jsonPath("$.participants[0].displayName").value("Ann"))
                .andExpect(jsonPath("$.participants[0].connected").value(true))
                .andExpect(jsonPath("$.participants[1].displayName").value("Ben"));
    }

    @Test
    void openApiDocumentsTheGameplayEndpoints() throws Exception {
        JsonNode paths = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("paths");

        assertThat(paths.has("/api/v1/sessions/{id}/questions/start")).isTrue();
        assertThat(paths.has("/api/v1/sessions/{id}/questions/advance")).isTrue();
        assertThat(paths.has("/api/v1/sessions/{id}/questions/current")).isTrue();
        assertThat(paths.has("/api/v1/sessions/{id}/results")).isTrue();
        assertThat(paths.has("/api/v1/sessions/{id}/leaderboard")).isTrue();
        assertThat(paths.has("/api/v1/sessions/{id}/answers")).isTrue();
        // answering is anonymous-friendly; host commands require bearer auth
        assertThat(paths.at("/~1api~1v1~1sessions~1{id}~1answers/post/security").isMissingNode()).isTrue();
        assertThat(paths.at("/~1api~1v1~1sessions~1{id}~1questions~1start/post/security/0/bearerAuth")
                .isMissingNode()).isFalse();
    }

    // --- flow helpers --------------------------------------------------------

    private String guestA;
    private String guestB;
    private String guestBToken;

    private String createLobbyWithTwoConnectedGuests(String hostToken, UUID quizId) throws Exception {
        JsonNode session = readJson(mockMvc.perform(post("/api/v1/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"publishedQuizVersionId": "%s"}
                                """.formatted(quizId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String sessionId = session.get("sessionId").asText();
        String pin = session.get("sessionPin").asText();

        mockMvc.perform(post("/api/v1/sessions/" + pin + "/lobby")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken)).andExpect(status().isOk());

        JsonNode a = joinGuest(pin, "Ann");
        JsonNode b = joinGuest(pin, "Ben");
        guestA = a.get("participantId").asText();
        guestB = b.get("participantId").asText();
        guestBToken = b.get("guestParticipantToken").asText();
        connect(a.get("guestParticipantToken").asText());
        connect(guestBToken);

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/start")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("IN_PROGRESS"));
        return sessionId;
    }

    private JsonNode joinGuest(String pin, String name) throws Exception {
        return readJson(mockMvc.perform(post("/api/v1/sessions/" + pin + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName": "%s", "preferredLanguage": "en"}
                                """.formatted(name)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void connect(String guestToken) throws Exception {
        mockMvc.perform(post("/api/v1/sessions/reconnect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"guestParticipantToken": "%s"}
                                """.formatted(guestToken)))
                .andExpect(status().isOk());
    }

    /**
     * Starts the next question and waits out its (1-second, this class)
     * reading period the same way a real client would — polling the
     * authoritative session read for the server's own scheduled
     * preview-to-open transition — so every test that just wants "a question
     * is open" can keep treating this as a single step.
     */
    private String openQuestion(String hostToken, String sessionId) throws Exception {
        String questionId = readJson(mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/start")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentPhase").value("QUESTION_PREVIEW"))
                .andReturn().getResponse().getContentAsString()).get("currentQuestionId").asText();
        awaitQuestionOpen(sessionId);
        return questionId;
    }

    private String advanceToNext(String hostToken, String sessionId) throws Exception {
        String questionId = readJson(mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/advance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentPhase").value("QUESTION_PREVIEW"))
                .andReturn().getResponse().getContentAsString()).get("currentQuestionId").asText();
        awaitQuestionOpen(sessionId);
        return questionId;
    }

    /** Polls the public session read until the server's own timer opens the question. */
    private void awaitQuestionOpen(String sessionId) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            JsonNode session = readJson(mockMvc.perform(get("/api/v1/sessions/" + sessionId))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            if ("QUESTION_OPEN".equals(session.get("currentPhase").asText())) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Question in session " + sessionId + " never opened after its reading period");
    }

    private void answer(String sessionId, String participantId, String questionId, UUID optionId)
            throws Exception {
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/answers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"participantId": "%s", "questionId": "%s", "selectedOptionIds": ["%s"]}
                                """.formatted(participantId, questionId, optionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.score").doesNotExist());
    }

    private void close(String hostToken, String sessionId) throws Exception {
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/close")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentPhase").value("QUESTION_CLOSED"));
    }

    private void reveal(String hostToken, String sessionId) throws Exception {
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/reveal")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentPhase").value("ANSWER_REVEALED"));
    }

    private JsonNode leaderboard(String hostToken, String sessionId) throws Exception {
        return readJson(mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/leaderboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /**
     * The real host onboarding journey, end to end over the API — no minted
     * tokens, no repository shortcuts (Phase 3 PR #1): register, log in,
     * request host access. The same token gains QUIZ_MASTER without a new
     * login because authorization reads persisted roles.
     */
    private HostAccount onboardHost() throws Exception {
        String email = "host-" + UUID.randomUUID() + "@example.com";
        JsonNode registered = readJson(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName": "Host", "email": "%s", "password": "StrongPassword@123"}
                                """.formatted(email)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String token = readJson(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "StrongPassword@123"}
                                """.formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("token").asText();
        mockMvc.perform(post("/api/v1/users/me/host-access")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("GRANTED"));
        UUID identityId = UUID.fromString(registered.get("identityId").asText());
        return new HostAccount(token, new IdentityReference(identityId, IdentityType.REGISTERED));
    }

    private record HostAccount(String token, IdentityReference reference) {
    }

    private PlayableQuiz publishedQuizWithTwoQuestions(IdentityReference owner) {
        LanguageCode en = LanguageCode.of("en");
        List<QuestionAnswers> questions = new ArrayList<>();
        Quiz quiz = Quiz.create(new QuizLocalization(en, "Bible Quiz", null), owner);
        for (int i = 1; i <= 2; i++) {
            Option correct = Option.of(true, 1);
            Option wrong = Option.of(false, 2);
            Question question = questionRepository.save(Question.create(
                    new QuestionLocalization(en, "Q" + i, "Prompt " + i, "Because of " + i),
                    owner, QuestionType.TRUE_FALSE, Difficulty.EASY,
                    List.of(correct, wrong),
                    List.of(correct.localized(en, "True"), wrong.localized(en, "False"))));
            quiz.addQuestion(question.getId());
            questions.add(new QuestionAnswers(question.getId(), correct.id(), wrong.id()));
        }
        quiz.publish();
        UUID quizId = quizRepository.save(quiz).getId();
        return new PlayableQuiz(quizId, questions);
    }

    private JsonNode readJson(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private record PlayableQuiz(UUID quizId, List<QuestionAnswers> questions) {
    }

    private record QuestionAnswers(UUID questionId, UUID correctOptionId, UUID wrongOptionId) {
    }
}
