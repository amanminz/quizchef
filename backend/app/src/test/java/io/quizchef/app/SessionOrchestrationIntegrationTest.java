package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizchef.identity.domain.Identity;
import io.quizchef.identity.domain.IdentityReference;
import io.quizchef.identity.domain.IdentitySession;
import io.quizchef.identity.domain.IdentityType;
import io.quizchef.identity.domain.Role;
import io.quizchef.identity.infrastructure.jwt.JwtTokenGenerator;
import io.quizchef.identity.infrastructure.persistence.IdentityRepository;
import io.quizchef.identity.infrastructure.persistence.IdentitySessionRepository;
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
import io.quizchef.websocket.api.Topics;
import java.util.List;
import java.util.Set;
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
 * The complete lobby flow end to end: a host creates a session for a
 * published quiz, opens the lobby, a guest and a registered player join, the
 * guest reconnects, and the host starts — verifying persistence, HTTP
 * contracts, authorization, and that every step's realtime message reaches
 * the session topic through the RFC-005 transport (broker template mocked).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SessionOrchestrationIntegrationTest {

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
    private IdentityRepository identityRepository;

    @Autowired
    private IdentitySessionRepository identitySessionRepository;

    @Autowired
    private JwtTokenGenerator tokenGenerator;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Test
    void fullLobbyFlow() throws Exception {
        Identity hostIdentity = identityRepository.save(Identity.registered());
        String hostToken = tokenFor(hostIdentity, Role.USER, Role.QUIZ_MASTER);
        UUID quizVersionId = publishedQuiz(hostIdentity.reference());

        // 1. host creates the session
        JsonNode session = readJson(mockMvc.perform(post("/api/v1/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"publishedQuizVersionId": "%s"}
                                """.formatted(quizVersionId)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.startsWith("/api/v1/sessions/")))
                .andExpect(jsonPath("$.state").value("CREATED"))
                .andExpect(jsonPath("$.sessionPin").value(org.hamcrest.Matchers.matchesPattern("\\d{6}")))
                .andReturn().getResponse().getContentAsString());
        String sessionId = session.get("sessionId").asText();
        String pin = session.get("sessionPin").asText();

        // 2. host opens the lobby
        mockMvc.perform(post("/api/v1/sessions/" + pin + "/lobby")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("LOBBY"));

        // 3. a guest joins (anonymous) and receives a reconnection token
        JsonNode guest = readJson(mockMvc.perform(post("/api/v1/sessions/" + pin + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName": "Guest Aman", "preferredLanguage": "kn"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestParticipantToken").isNotEmpty())
                .andExpect(jsonPath("$.sessionState").value("LOBBY"))
                .andReturn().getResponse().getContentAsString());
        String guestToken = guest.get("guestParticipantToken").asText();

        // 4. a registered player joins (no guest token)
        Identity playerIdentity = identityRepository.save(Identity.registered());
        String playerToken = tokenFor(playerIdentity, Role.USER);
        mockMvc.perform(post("/api/v1/sessions/" + pin + "/join")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + playerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName": "Aman", "preferredLanguage": "en"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestParticipantToken").doesNotExist());

        // 5. the guest reconnects and gets a snapshot
        mockMvc.perform(post("/api/v1/sessions/reconnect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"guestParticipantToken": "%s"}
                                """.formatted(guestToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.sessionState").value("LOBBY"))
                .andExpect(jsonPath("$.participantScore").value(0))
                .andExpect(jsonPath("$.leaderboard").isArray());

        // 6. host starts the session
        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/start")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IN_PROGRESS"));

        // 7. the summary reflects the roster; questions/gameplay are absent
        mockMvc.perform(get("/api/v1/sessions/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.participantCount").value(2))
                .andExpect(jsonPath("$.hostIdentityId").value(hostIdentity.getId().toString()));

        // persistence: two durable participants exist for the session
        assertThat(participantRepository.findBySessionId(UUID.fromString(sessionId))).hasSize(2);

        // realtime: every orchestration step reached the session topic as a protocol message
        var captor = org.mockito.ArgumentCaptor.forClass(ProtocolMessage.class);
        verify(messagingTemplate, times(5))
                .convertAndSend(eq(Topics.session(UUID.fromString(sessionId))), captor.capture());
        assertThat(captor.getAllValues()).extracting(ProtocolMessage::type)
                .containsExactly(
                        ProtocolMessageType.LOBBY_OPENED,
                        ProtocolMessageType.PARTICIPANT_JOINED,
                        ProtocolMessageType.PARTICIPANT_JOINED,
                        ProtocolMessageType.PARTICIPANT_RECONNECTED,
                        ProtocolMessageType.SESSION_STARTED);
    }

    @Test
    void anonymousCannotCreateASession() throws Exception {
        mockMvc.perform(post("/api/v1/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"publishedQuizVersionId": "%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aUserWithoutQuizHostCannotCreateASession() throws Exception {
        Identity identity = identityRepository.save(Identity.registered());
        String userToken = tokenFor(identity, Role.USER);

        mockMvc.perform(post("/api/v1/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"publishedQuizVersionId": "%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.permission.denied"));
    }

    @Test
    void cannotHostAnUnpublishedQuiz() throws Exception {
        Identity hostIdentity = identityRepository.save(Identity.registered());
        String hostToken = tokenFor(hostIdentity, Role.USER, Role.QUIZ_MASTER);
        UUID draftQuizId = draftQuiz(hostIdentity.reference());

        mockMvc.perform(post("/api/v1/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + hostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"publishedQuizVersionId": "%s"}
                                """.formatted(draftQuizId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quiz.not-published"));
    }

    @Test
    void openApiDocumentsTheSessionEndpoints() throws Exception {
        JsonNode paths = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("paths");

        assertThat(paths.has("/api/v1/sessions")).isTrue();
        assertThat(paths.has("/api/v1/sessions/{pin}/join")).isTrue();
        assertThat(paths.has("/api/v1/sessions/reconnect")).isTrue();
        assertThat(paths.has("/api/v1/sessions/{id}/start")).isTrue();
        // create requires bearer auth; anonymous join does not
        assertThat(paths.at("/~1api~1v1~1sessions/post/security/0/bearerAuth").isMissingNode()).isFalse();
        assertThat(paths.at("/~1api~1v1~1sessions~1{pin}~1join/post/security").isMissingNode()).isTrue();
    }

    private String tokenFor(Identity identity, Role... roles) {
        IdentitySession session = identitySessionRepository.save(
                IdentitySession.start(identity.getId(), "JUnit", "127.0.0.1", null));
        return tokenGenerator.generate(identity.getId(), session.getId(), IdentityType.REGISTERED,
                Set.of(roles)).token();
    }

    private UUID publishedQuiz(IdentityReference owner) {
        Quiz quiz = draftQuizAggregate(owner);
        quiz.publish();
        return quizRepository.save(quiz).getId();
    }

    private UUID draftQuiz(IdentityReference owner) {
        return quizRepository.save(draftQuizAggregate(owner)).getId();
    }

    private Quiz draftQuizAggregate(IdentityReference owner) {
        LanguageCode en = LanguageCode.of("en");
        Option trueOption = Option.of(true, 1);
        Option falseOption = Option.of(false, 2);
        Question question = questionRepository.save(Question.create(
                new QuestionLocalization(en, "Jonah", "Jonah was swallowed by a great fish.", null),
                owner, QuestionType.TRUE_FALSE, Difficulty.EASY,
                List.of(trueOption, falseOption),
                List.of(trueOption.localized(en, "True"), falseOption.localized(en, "False"))));

        Quiz quiz = Quiz.create(new QuizLocalization(en, "BELC Bible Quiz", "Weekly quiz"), owner);
        quiz.addQuestion(question.getId());
        return quiz;
    }

    private JsonNode readJson(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
