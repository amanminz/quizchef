package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    @Autowired
    private io.quizchef.quiz.application.GameplayQuizQuery gameplayQuizQuery;

    @Autowired
    private io.quizchef.quiz.application.GameplayQuestionContentQuery gameplayQuestionContentQuery;

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
        mockMvc.perform(post("/api/v1/sessions/" + sessionPin + "/participants/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resumeToken": "%s"}
                                """.formatted(guestBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPhase").value("QUESTION_OPEN"))
                .andExpect(jsonPath("$.currentQuestionId").value(q2))
                .andExpect(jsonPath("$.remainingMillis").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.leaderboard").isArray());

        // Finish the game. Q2 is the quiz's last question, so there is no
        // leaderboard step here at all — the standings belong to the
        // podium; the host finishes straight from the reveal.
        answer(sessionId, guestA, q2, quiz.questions().get(1).correctOptionId());
        close(hostToken, sessionId);
        reveal(hostToken, sessionId);
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/leaderboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.leaderboard.not-available"));
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
                .andExpect(jsonPath("$.score").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.pointsEarned").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.totalQuestions").value(2))
                .andExpect(jsonPath("$.participantCount").value(2))
                // Their own progress, and no standing of any kind: no rank
                // of their own, and no other player's row.
                .andExpect(jsonPath("$.rank").doesNotExist())
                .andExpect(jsonPath("$.entries").doesNotExist());
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/" + guestB + "/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantId").value(guestB))
                .andExpect(jsonPath("$.rank").doesNotExist())
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.pointsEarned").value(0));

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
                .andExpect(jsonPath("$.score").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.rank").doesNotExist());
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

        // Still IN_PROGRESS — the host hasn't clicked "Finish Quiz" yet —
        // but q2 is the quiz's last question, so the hold must already
        // apply here, one host click before the session technically
        // finishes. This is the exact window the projector-layout hotfix
        // closed: a participant must never see their real final rank just
        // because the session state hasn't flipped yet.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/" + guestA + "/result"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.results.not-available"));

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

        // Participants see nothing about their finish while pending —
        // neither the progress read nor the placement one answers.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/" + guestA + "/result"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.results.not-available"));
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/" + guestA
                        + "/final-placement"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.results.not-available"));

        // Only the host may release, and only once the session has finished.
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/results/release"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/results/release")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalResultsReleased").value(true));

        // Now every participant may read their own finish. This room has
        // two players, so both are inside the reveal group and both get an
        // exact rank; the split is exercised properly below.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/" + guestA
                        + "/final-placement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("EXACT_RANK"))
                .andExpect(jsonPath("$.rank").value(1))
                .andExpect(jsonPath("$.label").value("WINNER"));
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/" + guestB
                        + "/final-placement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(2));

        // A duplicate release is harmless.
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/results/release")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalResultsReleased").value(true));
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/participants/" + guestA
                        + "/final-placement"))
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
    void topFiveTransitionIsHostOnlyAndTheFinalQuestionHasNoLeaderboardAtAll() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithTwoQuestions(host.reference());
        String sessionId = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());

        String q1 = openQuestion(hostToken, sessionId);
        answer(sessionId, guestA, q1, quiz.questions().get(0).correctOptionId());
        answer(sessionId, guestB, q1, quiz.questions().get(0).wrongOptionId());

        // Standings before the reveal would leak who answered correctly.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/leaderboard/top-five")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.top-five.not-available"));

        close(hostToken, sessionId);
        reveal(hostToken, sessionId);

        // A participant device holds no host token: the projected board —
        // every name, score, and rank on it — is the host's alone.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/leaderboard/top-five"))
                .andExpect(status().isUnauthorized());

        // Both boards, from the server: Ann answered correctly and takes
        // the lead she did not hold before the question.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/leaderboard/top-five")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionNumber").value(1))
                .andExpect(jsonPath("$.totalQuestions").value(2))
                .andExpect(jsonPath("$.finalQuestion").value(false))
                .andExpect(jsonPath("$.previousTopFive", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.currentTopFive", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.currentTopFive[0].participantId").value(guestA))
                .andExpect(jsonPath("$.currentTopFive[0].displayName").value("Ann"))
                .andExpect(jsonPath("$.currentTopFive[0].currentRank").value(1))
                .andExpect(jsonPath("$.currentTopFive[0].previousScore").value(0))
                .andExpect(jsonPath("$.currentTopFive[0].pointsEarned")
                        .value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.currentTopFive[1].pointsEarned").value(0));

        // A non-final question still passes through its standings: the host
        // cannot skip them by advancing straight from the reveal.
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/advance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.invalid-transition"));

        leaderboard(hostToken, sessionId);
        String q2 = advanceToNext(hostToken, sessionId);
        answer(sessionId, guestB, q2, quiz.questions().get(1).correctOptionId());
        close(hostToken, sessionId);
        reveal(hostToken, sessionId);

        // Q2 is the quiz's last. There is no interim board to animate, and
        // no leaderboard step to enter — both are refused outright, so no
        // client can put the finishing order on the projector ahead of the
        // podium. The host's step here is to finish the session.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/leaderboard/top-five")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.top-five.not-available"));
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/leaderboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.leaderboard.not-available"));
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/advance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FINISHED"));

        // And the host's own full standings — the podium's source — are
        // untouched by any of it.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/results")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void finalPlacementSplitsTheRoomAtTheBackendCutoff() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithOneQuestion(host.reference());

        // Six players, each scoring a little less than the last, so the
        // ranking is unambiguous end to end. Six → the reveal group is
        // ranks 1–5, leaving exactly one player in the relative-only group.
        Lobby lobby = createLobbyWithGuests(hostToken, quiz.quizId(),
                List.of("Amelia", "Aman", "David", "Ruth", "John", "Grace"));
        String questionId = openQuestion(hostToken, lobby.sessionId());
        for (int index = 0; index < lobby.participantIds().size(); index++) {
            // Everyone answers correctly; the scoring engine's speed bonus
            // is what separates them, in join order.
            answer(lobby.sessionId(), lobby.participantIds().get(index), questionId,
                    quiz.questions().get(0).correctOptionId());
        }
        close(hostToken, lobby.sessionId());
        reveal(hostToken, lobby.sessionId());

        // This quiz has one question, so that reveal was the last one: the
        // whole room sits on the announcement-waiting screen, with neither
        // read answering, until the host has run the ceremony.
        for (String participantId : lobby.participantIds()) {
            mockMvc.perform(get("/api/v1/sessions/" + lobby.sessionId() + "/participants/"
                            + participantId + "/result"))
                    .andExpect(status().isConflict());
            mockMvc.perform(get("/api/v1/sessions/" + lobby.sessionId() + "/participants/"
                            + participantId + "/final-placement"))
                    .andExpect(status().isConflict());
        }

        mockMvc.perform(post("/api/v1/sessions/" + lobby.sessionId() + "/questions/advance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("FINISHED"));

        // The host's own read carries the whole field plus the cutoff that
        // says how much of it belongs on the projector.
        JsonNode results = readJson(mockMvc.perform(
                        get("/api/v1/sessions/" + lobby.sessionId() + "/results")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exactRankRevealCount").value(5))
                .andExpect(jsonPath("$.entries", org.hamcrest.Matchers.hasSize(6)))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/v1/sessions/" + lobby.sessionId() + "/results/release")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken)).andExpect(status().isOk());

        // Ranks 1–5 learn exactly where they came, with the right label.
        for (int rank = 1; rank <= 5; rank++) {
            String participantId = results.get("entries").get(rank - 1).get("participantId").asText();
            String expectedLabel = rank <= 3 ? "WINNER" : "RUNNER_UP";
            mockMvc.perform(get("/api/v1/sessions/" + lobby.sessionId() + "/participants/"
                            + participantId + "/final-placement"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.visibility").value("EXACT_RANK"))
                    .andExpect(jsonPath("$.rank").value(rank))
                    .andExpect(jsonPath("$.label").value(expectedLabel));
        }

        // The sixth gets their score and their neighbours' names — and no
        // position, no neighbour rank, no neighbour score, no gap.
        String sixth = results.get("entries").get(5).get("participantId").asText();
        String fifthName = results.get("entries").get(4).get("displayName").asText();
        mockMvc.perform(get("/api/v1/sessions/" + lobby.sessionId() + "/participants/" + sixth
                        + "/final-placement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("RELATIVE_ONLY"))
                .andExpect(jsonPath("$.rank").doesNotExist())
                .andExpect(jsonPath("$.label").doesNotExist())
                .andExpect(jsonPath("$.score").exists())
                .andExpect(jsonPath("$.behind.displayName").value(fifthName))
                .andExpect(jsonPath("$.behind.rank").doesNotExist())
                .andExpect(jsonPath("$.behind.score").doesNotExist())
                // Nobody finished below them.
                .andExpect(jsonPath("$.aheadOf").doesNotExist())
                .andExpect(jsonPath("$.entries").doesNotExist());
    }

    @Test
    void finishedSessionsKeepTheirStandingsAsHistory() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithOneQuestion(host.reference());
        Lobby lobby = createLobbyWithGuests(hostToken, quiz.quizId(),
                List.of("Amelia", "Aman", "Ruth"));

        // Nothing to show before the session ends — an honest empty, not an
        // error and not a reconstruction that would look authoritative.
        mockMvc.perform(get("/api/v1/sessions/" + lobby.sessionId() + "/final-standings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(jsonPath("$.capturedAt").doesNotExist());

        String questionId = openQuestion(hostToken, lobby.sessionId());
        for (String participantId : lobby.participantIds()) {
            answer(lobby.sessionId(), participantId, questionId,
                    quiz.questions().get(0).correctOptionId());
        }
        close(hostToken, lobby.sessionId());
        reveal(hostToken, lobby.sessionId());
        mockMvc.perform(post("/api/v1/sessions/" + lobby.sessionId() + "/questions/advance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("FINISHED"));

        // Every participant, in the order the game produced, with the name
        // they played under and the score they finished on.
        JsonNode history = readJson(mockMvc.perform(
                        get("/api/v1/sessions/" + lobby.sessionId() + "/final-standings")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.capturedAt").exists())
                .andExpect(jsonPath("$.entries[0].rank").value(1))
                .andExpect(jsonPath("$.entries[1].rank").value(2))
                .andExpect(jsonPath("$.entries[2].rank").value(3))
                .andReturn().getResponse().getContentAsString());
        assertThat(history.get("entries").get(0).get("score").asInt()).isPositive();
        assertThat(history.get("entries")).allSatisfy(entry ->
                assertThat(entry.get("displayName").asText()).isNotBlank());

        // Reading it again returns the identical snapshot — it is stored,
        // not recomputed, so nothing about it can drift.
        mockMvc.perform(get("/api/v1/sessions/" + lobby.sessionId() + "/final-standings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(content().json(history.toString(), true));

        // Host data. A participant device holds no host token and is refused
        // the whole field — their own finish comes from the placement read,
        // which applies the reveal-group policy this one deliberately does not.
        mockMvc.perform(get("/api/v1/sessions/" + lobby.sessionId() + "/final-standings"))
                .andExpect(status().isUnauthorized());

        // And another host cannot read someone else's event.
        HostAccount stranger = onboardHost();
        mockMvc.perform(get("/api/v1/sessions/" + lobby.sessionId() + "/final-standings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shufflingChangesTheOrderThisSessionPlaysWithoutTouchingTheQuiz() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithTwoQuestions(host.reference());
        String sessionId = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());

        // A quiz plays its authored order unless a session says otherwise.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionsShuffled").value(false));

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/shuffle")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionsShuffled").value(true));

        // Whatever order was drawn, the session plays a permutation of the
        // same questions — the quiz's own content is untouched, so a second
        // session of it still starts from the authored order.
        String firstQuestionId = openQuestion(hostToken, sessionId);
        assertThat(List.of(quiz.questions().get(0).questionId().toString(),
                        quiz.questions().get(1).questionId().toString()))
                .contains(firstQuestionId);

        String otherSession = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());
        mockMvc.perform(get("/api/v1/sessions/" + otherSession))
                .andExpect(jsonPath("$.questionsShuffled").value(false));

        // Numbering follows the order actually being played, so "Question 1
        // of 2" means the first question of *this* session.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/questions/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionNumber").value(1))
                .andExpect(jsonPath("$.totalQuestions").value(2));
    }

    @Test
    void shufflingIsRefusedOnceAQuestionHasBeenPlayed() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithTwoQuestions(host.reference());
        String sessionId = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());
        openQuestion(hostToken, sessionId);

        // Renumbering a game in progress would deal a question the room has
        // already answered.
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/shuffle")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.invalid-transition"));

        // Host-only, like every other session command.
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/shuffle"))
                .andExpect(status().isUnauthorized());
        HostAccount stranger = onboardHost();
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/shuffle")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger.token()))
                .andExpect(status().isForbidden());
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
        assertThat(paths.has("/api/v1/sessions/{id}/leaderboard/top-five")).isTrue();
        assertThat(paths.at("/~1api~1v1~1sessions~1{id}~1leaderboard~1top-five/get/security/0/bearerAuth")
                .isMissingNode()).isFalse();
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
    /** The PIN of the lobby the current test is playing in — resume is addressed by it. */
    private String sessionPin;

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
        sessionPin = pin;

        mockMvc.perform(post("/api/v1/sessions/" + pin + "/lobby")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken)).andExpect(status().isOk());

        JsonNode a = joinGuest(pin, "Ann");
        JsonNode b = joinGuest(pin, "Ben");
        guestA = a.get("participantId").asText();
        guestB = b.get("participantId").asText();
        guestBToken = b.get("guestParticipantToken").asText();
        connect(pin, a.get("guestParticipantToken").asText());
        connect(pin, guestBToken);

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

    private void connect(String pin, String resumeToken) throws Exception {
        mockMvc.perform(post("/api/v1/sessions/" + pin + "/participants/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resumeToken": "%s"}
                                """.formatted(resumeToken)))
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

    /**
     * A one-question quiz, so a session finishes after a single round —
     * enough to exercise the finishing order without playing a whole game.
     */
    private PlayableQuiz publishedQuizWithOneQuestion(IdentityReference owner) {
        LanguageCode en = LanguageCode.of("en");
        Option correct = Option.of(true, 1);
        Option wrong = Option.of(false, 2);
        Quiz quiz = Quiz.create(new QuizLocalization(en, "Bible Quiz", null), owner);
        Question question = questionRepository.save(Question.create(
                new QuestionLocalization(en, "Q1", "Prompt 1", "Because of 1"),
                owner, QuestionType.TRUE_FALSE, Difficulty.EASY,
                List.of(correct, wrong),
                List.of(correct.localized(en, "True"), wrong.localized(en, "False"))));
        quiz.addQuestion(question.getId());
        quiz.publish();
        UUID quizId = quizRepository.save(quiz).getId();
        return new PlayableQuiz(quizId,
                List.of(new QuestionAnswers(question.getId(), correct.id(), wrong.id())));
    }

    /** A started session with as many connected guests as names given. */
    private Lobby createLobbyWithGuests(String hostToken, UUID quizId, List<String> names)
            throws Exception {
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

        List<String> participantIds = new ArrayList<>();
        for (String name : names) {
            JsonNode guest = joinGuest(pin, name);
            participantIds.add(guest.get("participantId").asText());
            connect(pin, guest.get("guestParticipantToken").asText());
        }

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/start")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("IN_PROGRESS"));
        return new Lobby(sessionId, participantIds);
    }

    private record Lobby(String sessionId, List<String> participantIds) {
    }


    // --- Correcting and removing a question mid-session (RFC-020) ---

    @Test
    void removingAnUpcomingQuestionRenumbersTheSequenceWithNoGap() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithQuestions(host.reference(), 3);
        String sessionId = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());
        openQuestion(hostToken, sessionId);

        removeQuestion(hostToken, sessionId, quiz.questions().get(1).questionId());

        // The room must never see "Question 1 of 3" become "Question 3 of 3":
        // the sequence closes up, and the count closes with it.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/questions/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionNumber").value(1))
                .andExpect(jsonPath("$.totalQuestions").value(2));

        close(hostToken, sessionId);
        reveal(hostToken, sessionId);
        leaderboard(hostToken, sessionId);
        String next = advanceToNext(hostToken, sessionId);

        assertThat(next).isEqualTo(quiz.questions().get(2).questionId().toString());
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/questions/current"))
                .andExpect(jsonPath("$.questionNumber").value(2))
                .andExpect(jsonPath("$.totalQuestions").value(2));
    }

    @Test
    void removingTheAnsweredQuestionInPlayCancelsItsScoresAndOpensTheNext() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithQuestions(host.reference(), 3);
        String sessionId = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());
        String q1 = openQuestion(hostToken, sessionId);
        answer(sessionId, guestA, q1, quiz.questions().get(0).correctOptionId());
        answer(sessionId, guestB, q1, quiz.questions().get(0).wrongOptionId());

        removeQuestion(hostToken, sessionId, quiz.questions().get(0).questionId());

        // Points awarded by the removed question are gone from the source of
        // truth, so every projection over them follows without being told.
        assertThat(participantRepository.findBySessionId(UUID.fromString(sessionId)))
                .allSatisfy(participant -> assertThat(participant.getTotalScore()).isZero());

        // The next question takes over — with its own reading period, and
        // without ever revealing the removed question's answer.
        awaitQuestionOpen(sessionId);
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/questions/current"))
                .andExpect(jsonPath("$.questionId").value(quiz.questions().get(1).questionId().toString()))
                .andExpect(jsonPath("$.questionNumber").value(1))
                .andExpect(jsonPath("$.totalQuestions").value(2))
                .andExpect(jsonPath("$.correctOptionIds").doesNotExist());

        // And no answer survives: the host's progress read counts the new
        // question from zero rather than carrying the cancelled attempt over.
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/answer-progress")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answeredCount").value(0));

        // The room is told, additively and without a spoiler: the removal
        // rides the wire as a bare notification, and the removed question's
        // answer never reaches the session topic on its account.
        var captor = org.mockito.ArgumentCaptor.forClass(ProtocolMessage.class);
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(startsWith("/topic/session/"), captor.capture());
        assertThat(captor.getAllValues()).extracting(ProtocolMessage::type)
                .contains(ProtocolMessageType.QUESTION_REMOVED);
        assertThat(captor.getAllValues())
                .filteredOn(message -> message.type() == ProtocolMessageType.QUESTION_REMOVED)
                .singleElement()
                .satisfies(message -> assertThat(message.payload().toString())
                        .doesNotContain(quiz.questions().getFirst().correctOptionId().toString()));
    }

    @Test
    void correctingTheQuestionInPlayReplaysItAndLeavesTheLibraryUntouched() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithQuestions(host.reference(), 2);
        String sessionId = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());
        QuestionAnswers first = quiz.questions().getFirst();
        String q1 = openQuestion(hostToken, sessionId);
        answer(sessionId, guestA, q1, first.correctOptionId());

        // The host discovers the answer key is wrong: what was marked wrong
        // is in fact the right answer.
        correctQuestion(hostToken, sessionId, first.questionId(), first.wrongOptionId(),
                "Corrected prompt");

        // The published question is not a party to any of this — read back
        // through the quiz module's own gameplay boundary, which is what a
        // *different* session of the same quiz would see.
        assertThat(gameplayQuizQuery.load(quiz.quizId()).questions())
                .filteredOn(question -> question.questionId().equals(first.questionId()))
                .singleElement()
                .satisfies(question ->
                        assertThat(question.correctOptionIds()).containsExactly(first.correctOptionId()));
        assertThat(gameplayQuestionContentQuery.content(first.questionId()).localizations())
                .singleElement()
                .satisfies(localization -> assertThat(localization.prompt()).isEqualTo("Prompt 1"));

        // The attempt is cancelled and the same question restarts.
        assertThat(participantRepository.findBySessionId(UUID.fromString(sessionId)))
                .allSatisfy(participant -> assertThat(participant.getTotalScore()).isZero());
        awaitQuestionOpen(sessionId);
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/questions/current"))
                .andExpect(jsonPath("$.questionId").value(first.questionId().toString()))
                .andExpect(jsonPath("$.localizations[0].prompt").value("Corrected prompt"));

        // And it is now scored against the corrected key.
        answer(sessionId, guestA, q1, first.wrongOptionId());
        close(hostToken, sessionId);
        reveal(hostToken, sessionId);
        JsonNode board = leaderboard(hostToken, sessionId);
        assertThat(board.get("entries").get(0).get("participantId").asText()).isEqualTo(guestA);
        assertThat(board.get("entries").get(0).get("score").asInt()).isGreaterThan(0);
    }

    @Test
    void removingTheLastRemainingQuestionFinishesTheSession() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithTwoQuestions(host.reference());
        String sessionId = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());
        String q1 = openQuestion(hostToken, sessionId);
        answer(sessionId, guestA, q1, quiz.questions().getFirst().correctOptionId());
        close(hostToken, sessionId);
        reveal(hostToken, sessionId);
        leaderboard(hostToken, sessionId);
        advanceToNext(hostToken, sessionId);

        // Nothing follows the last question, so removing it ends the quiz —
        // no leaderboard for a question the room never completed.
        removeQuestion(hostToken, sessionId, quiz.questions().get(1).questionId());

        mockMvc.perform(get("/api/v1/sessions/" + sessionId))
                .andExpect(jsonPath("$.state").value("FINISHED"))
                .andExpect(jsonPath("$.finalResultsReleased").value(false));
        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/final-standings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2));
    }

    @Test
    void aSessionCannotBeEmptiedOfQuestions() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithTwoQuestions(host.reference());
        String sessionId = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());
        openQuestion(hostToken, sessionId);
        removeQuestion(hostToken, sessionId, quiz.questions().get(1).questionId());

        // One question left, and a quiz with nothing to ask cannot produce a
        // result worth showing.
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/"
                        + quiz.questions().getFirst().questionId() + "/removal")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("session.question.removal-not-allowed"));
    }

    @Test
    void aDoubleRemovalConvergesRatherThanConflicting() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithQuestions(host.reference(), 3);
        String sessionId = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());
        openQuestion(hostToken, sessionId);
        UUID upcoming = quiz.questions().get(2).questionId();

        removeQuestion(hostToken, sessionId, upcoming);
        // The host's second click, or a retry of the first: it must not
        // restore the question and must not fail.
        removeQuestion(hostToken, sessionId, upcoming);

        mockMvc.perform(get("/api/v1/sessions/" + sessionId + "/questions/current"))
                .andExpect(jsonPath("$.totalQuestions").value(2));
    }

    @Test
    void correctionRemovalAndTheQuestionListAreHostOnly() throws Exception {
        HostAccount host = onboardHost();
        String hostToken = host.token();
        PlayableQuiz quiz = publishedQuizWithTwoQuestions(host.reference());
        String sessionId = createLobbyWithTwoConnectedGuests(hostToken, quiz.quizId());
        UUID questionId = quiz.questions().getFirst().questionId();
        String removal = "/api/v1/sessions/" + sessionId + "/questions/" + questionId + "/removal";
        String correction = "/api/v1/sessions/" + sessionId + "/questions/" + questionId + "/correction";
        String list = "/api/v1/sessions/" + sessionId + "/questions";
        HostAccount stranger = onboardHost();

        // A participant device has no token at all — and the question list
        // carries unrevealed answer keys, so this is the gate that matters.
        mockMvc.perform(post(removal)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(list)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(correction).contentType(MediaType.APPLICATION_JSON)
                        .content(correctionBody(quiz.questions().getFirst().wrongOptionId(), "x")))
                .andExpect(status().isUnauthorized());

        // Hosting is exclusive to the identity that created the session.
        mockMvc.perform(post(removal).header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(list).header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(correction)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctionBody(quiz.questions().getFirst().wrongOptionId(), "x")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(list).header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuestions").value(2))
                .andExpect(jsonPath("$.questions[0].status").value("UPCOMING"));
    }

    private void removeQuestion(String hostToken, String sessionId, UUID questionId) throws Exception {
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/" + questionId + "/removal")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk());
    }

    private void correctQuestion(String hostToken, String sessionId, UUID questionId,
                                 UUID newCorrectOptionId, String prompt) throws Exception {
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/questions/" + questionId + "/correction")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(correctionBody(newCorrectOptionId, prompt)))
                .andExpect(status().isOk());
    }

    private static String correctionBody(UUID correctOptionId, String prompt) {
        return """
                {"correctOptionIds": ["%s"],
                 "localizations": [{"languageCode": "en", "prompt": "%s", "options": []}]}
                """.formatted(correctOptionId, prompt);
    }

    private PlayableQuiz publishedQuizWithTwoQuestions(IdentityReference owner) {
        return publishedQuizWithQuestions(owner, 2);
    }

    private PlayableQuiz publishedQuizWithQuestions(IdentityReference owner, int count) {
        LanguageCode en = LanguageCode.of("en");
        List<QuestionAnswers> questions = new ArrayList<>();
        Quiz quiz = Quiz.create(new QuizLocalization(en, "Bible Quiz", null), owner);
        for (int i = 1; i <= count; i++) {
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
