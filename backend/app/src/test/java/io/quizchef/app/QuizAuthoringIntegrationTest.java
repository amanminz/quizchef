package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizchef.identity.domain.Identity;
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
import io.quizchef.quiz.domain.event.QuizArchivedEvent;
import io.quizchef.quiz.domain.event.QuizCreatedEvent;
import io.quizchef.quiz.domain.event.QuizPublishedEvent;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end quiz authoring: the full create → edit → attach → publish →
 * archive workflow over HTTP, plus authorization, ownership, optimistic
 * locking, and OpenAPI documentation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class QuizAuthoringIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration(proxyBeanMethods = false)
    static class EventCapture {

        final List<QuizCreatedEvent> created = new CopyOnWriteArrayList<>();
        final List<QuizPublishedEvent> published = new CopyOnWriteArrayList<>();
        final List<QuizArchivedEvent> archived = new CopyOnWriteArrayList<>();

        @EventListener
        void on(QuizCreatedEvent event) {
            created.add(event);
        }

        @EventListener
        void on(QuizPublishedEvent event) {
            published.add(event);
        }

        @EventListener
        void on(QuizArchivedEvent event) {
            archived.add(event);
        }
    }

    private static final String CREATE_QUIZ_JSON = """
            {
              "defaultLanguage": "en",
              "visibility": "PRIVATE",
              "localization": {
                "languageCode": "en",
                "title": "Bible Quiz",
                "description": "Sunday Youth Fellowship"
              },
              "settings": {
                "questionTimeLimitSeconds": 30,
                "randomizeQuestionOrder": false,
                "randomizeOptionOrder": false,
                "showLeaderboardAfterQuestion": true,
                "showExplanationAfterQuestion": true
              }
            }
            """;

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
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EventCapture eventCapture;

    @BeforeEach
    void resetCapturedEvents() {
        eventCapture.created.clear();
        eventCapture.published.clear();
        eventCapture.archived.clear();
    }

    @Test
    void fullAuthoringWorkflow() throws Exception {
        String token = quizMasterToken();

        // create a draft
        JsonNode quiz = readJson(mockMvc.perform(post("/api/v1/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_QUIZ_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.startsWith("/api/v1/quizzes/")))
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.defaultLanguage").value("en"))
                .andReturn().getResponse().getContentAsString());
        String quizId = quiz.get("id").asText();
        assertThat(eventCapture.created).singleElement()
                .satisfies(event -> assertThat(event.quizId()).hasToString(quizId));

        // edit metadata: add a Kannada translation, slow the pace
        JsonNode updated = readJson(mockMvc.perform(put("/api/v1/quizzes/" + quizId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "settings": {
                                    "questionTimeLimitSeconds": 60,
                                    "randomizeQuestionOrder": false,
                                    "randomizeOptionOrder": false,
                                    "showLeaderboardAfterQuestion": true,
                                    "showExplanationAfterQuestion": true
                                  },
                                  "localizations": [
                                    {"languageCode": "en", "title": "Bible Quiz", "description": "Sunday Youth Fellowship"},
                                    {"languageCode": "kn", "title": "ಬೈಬಲ್ ರಸಪ್ರಶ್ನೆ", "description": null}
                                  ]
                                }
                                """.formatted(quiz.get("version").asLong())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.questionTimeLimitSeconds").value(60))
                .andExpect(jsonPath("$.localizations.length()").value(2))
                .andReturn().getResponse().getContentAsString());

        // publishing an empty quiz fails
        mockMvc.perform(post("/api/v1/quizzes/" + quizId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quiz.not-publishable"));

        // author questions and attach them (question APIs arrive in the next PR)
        UUID first = questionRepository.save(sampleQuestion("en")).getId();
        UUID second = questionRepository.save(sampleQuestion("en")).getId();
        attachQuestion(quizId, first);
        attachQuestion(quizId, second);

        // publish
        JsonNode publishedQuiz = readJson(mockMvc.perform(post("/api/v1/quizzes/" + quizId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PUBLISHED"))
                .andReturn().getResponse().getContentAsString());
        assertThat(eventCapture.published).singleElement()
                .satisfies(event -> assertThat(event.quizId()).hasToString(quizId));

        // published quizzes accept nothing but visibility
        mockMvc.perform(put("/api/v1/quizzes/" + quizId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": %d,
                                  "localizations": [
                                    {"languageCode": "en", "title": "Sneaky rename", "description": null}
                                  ]
                                }
                                """.formatted(publishedQuiz.get("version").asLong())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quiz.content.locked"));

        mockMvc.perform(put("/api/v1/quizzes/" + quizId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "visibility": "PUBLIC"}
                                """.formatted(publishedQuiz.get("version").asLong())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));

        // archive, then nothing changes anymore
        JsonNode archivedQuiz = readJson(mockMvc.perform(post("/api/v1/quizzes/" + quizId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ARCHIVED"))
                .andReturn().getResponse().getContentAsString());
        assertThat(eventCapture.archived).singleElement()
                .satisfies(event -> assertThat(event.quizId()).hasToString(quizId));

        mockMvc.perform(put("/api/v1/quizzes/" + quizId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": %d, "visibility": "PRIVATE"}
                                """.formatted(archivedQuiz.get("version").asLong())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quiz.archived"));

        // the read model keeps everything: state, localizations, ordered questions
        mockMvc.perform(get("/api/v1/quizzes/" + quizId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ARCHIVED"))
                .andExpect(jsonPath("$.localizations.length()").value(2))
                .andExpect(jsonPath("$.questionIds[0]").value(first.toString()))
                .andExpect(jsonPath("$.questionIds[1]").value(second.toString()));
        assertThat(updated.get("version").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void publishRequiresQuestionsLocalizedInTheQuizDefaultLanguage() throws Exception {
        String token = quizMasterToken();
        JsonNode quiz = readJson(mockMvc.perform(post("/api/v1/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "defaultLanguage": "kn",
                                  "localization": {"languageCode": "kn", "title": "ಬೈಬಲ್ ರಸಪ್ರಶ್ನೆ", "description": null}
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String quizId = quiz.get("id").asText();

        Question englishOnly = questionRepository.save(sampleQuestion("en"));
        attachQuestion(quizId, englishOnly.getId());

        mockMvc.perform(post("/api/v1/quizzes/" + quizId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quiz.not-publishable"));

        localizeQuestionIn(englishOnly.getId(), "kn");

        mockMvc.perform(post("/api/v1/quizzes/" + quizId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PUBLISHED"));
    }

    @Test
    void staleVersionsAreRejectedInsteadOfSilentlyOverwriting() throws Exception {
        String token = quizMasterToken();
        String quizId = createQuiz(token);

        // author A saves against version 0
        mockMvc.perform(put("/api/v1/quizzes/" + quizId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "visibility": "UNLISTED"}
                                """))
                .andExpect(status().isOk());

        // author B, still holding version 0, loses
        mockMvc.perform(put("/api/v1/quizzes/" + quizId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "visibility": "PUBLIC"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quiz.concurrent-modification"));
    }

    @Test
    void draftsCannotBeArchived() throws Exception {
        String token = quizMasterToken();
        String quizId = createQuiz(token);

        mockMvc.perform(post("/api/v1/quizzes/" + quizId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quiz.not-archivable"));
    }

    @Test
    void anonymousCallersAreUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/quizzes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_QUIZ_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.unauthorized"));
    }

    @Test
    void usersWithoutQuizCreateAreForbidden() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(post("/api/v1/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_QUIZ_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.permission.denied"));
    }

    @Test
    void ownershipComesFromCurrentUserAndIsEnforced() throws Exception {
        String ownerToken = quizMasterToken();
        String strangerToken = quizMasterToken();
        String quizId = createQuiz(ownerToken);

        // a private quiz does not reveal its existence to others
        mockMvc.perform(get("/api/v1/quizzes/" + quizId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("quiz.not-found"));
        mockMvc.perform(put("/api/v1/quizzes/" + quizId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "visibility": "PUBLIC"}
                                """))
                .andExpect(status().isNotFound());

        // once public it is readable by anyone with QUIZ_VIEW, but still owner-modifiable only
        mockMvc.perform(put("/api/v1/quizzes/" + quizId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 0, "visibility": "PUBLIC"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/quizzes/" + quizId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/quizzes/" + quizId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version": 1, "visibility": "PRIVATE"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("quiz.ownership.required"));

        // the created quiz is owned by the caller's identity, never client-supplied
        mockMvc.perform(get("/api/v1/quizzes/" + quizId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(jsonPath("$.ownerIdentityId").isNotEmpty());
    }

    @Test
    void openApiDocumentsTheQuizEndpoints() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(body).get("paths");

        assertThat(paths.has("/api/v1/quizzes")).isTrue();
        assertThat(paths.has("/api/v1/quizzes/{quizId}")).isTrue();
        assertThat(paths.has("/api/v1/quizzes/{quizId}/publish")).isTrue();
        assertThat(paths.has("/api/v1/quizzes/{quizId}/archive")).isTrue();
        assertThat(paths.at("/~1api~1v1~1quizzes/post/security/0/bearerAuth").isMissingNode())
                .as("create quiz documents bearer authentication")
                .isFalse();
    }

    private String createQuiz(String token) throws Exception {
        JsonNode quiz = readJson(mockMvc.perform(post("/api/v1/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_QUIZ_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return quiz.get("id").asText();
    }

    private JsonNode readJson(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private String quizMasterToken() {
        Identity identity = identityRepository.save(Identity.registered());
        IdentitySession session = identitySessionRepository.save(
                IdentitySession.start(identity.getId(), "JUnit", "127.0.0.1", null));
        return tokenGenerator.generate(identity.getId(), session.getId(), IdentityType.REGISTERED,
                Set.of(Role.USER, Role.QUIZ_MASTER)).token();
    }

    private String registerAndLogin() throws Exception {
        String email = "author-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Aman Minz",
                                  "email": "%s",
                                  "password": "StrongPassword@123"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "StrongPassword@123"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private void attachQuestion(String quizId, UUID questionId) {
        transactionTemplate.executeWithoutResult(status -> {
            Quiz quiz = quizRepository.findById(UUID.fromString(quizId)).orElseThrow();
            quiz.addQuestion(questionId);
        });
    }

    private void localizeQuestionIn(UUID questionId, String language) {
        transactionTemplate.executeWithoutResult(status -> {
            Question question = questionRepository.findById(questionId).orElseThrow();
            LanguageCode languageCode = LanguageCode.of(language);
            question.localize(
                    new QuestionLocalization(languageCode, "ಯೋನ", "ಯೋನನನ್ನು ದೊಡ್ಡ ಮೀನು ನುಂಗಿತು.", null),
                    question.options().stream()
                            .map(option -> option.localized(languageCode, "ಆಯ್ಕೆ " + option.displayOrder()))
                            .toList());
        });
    }

    private Question sampleQuestion(String language) {
        LanguageCode languageCode = LanguageCode.of(language);
        Option trueOption = Option.of(true, 1);
        Option falseOption = Option.of(false, 2);
        return Question.create(
                new QuestionLocalization(languageCode, "Jonah", "Jonah was swallowed by a great fish.", null),
                QuestionType.TRUE_FALSE, Difficulty.EASY,
                List.of(trueOption, falseOption),
                List.of(trueOption.localized(languageCode, "True"),
                        falseOption.localized(languageCode, "False")));
    }
}
