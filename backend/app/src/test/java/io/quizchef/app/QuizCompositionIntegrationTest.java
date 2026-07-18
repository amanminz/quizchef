package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end quiz composition: the capabilities RFC-003 reserved as "next
 * steps" and Phase 2 PR #2 needed to build against — listing "my quizzes",
 * searching the question library, and attaching/detaching/reordering
 * questions on a quiz — all over HTTP, with their validation and
 * authorization rules.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class QuizCompositionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

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

    @Test
    void authorWorkflowCreateListAttachReorderRemovePublish() throws Exception {
        String token = quizMasterToken();

        String quizId = createQuiz(token, "en", "Bible Quiz");
        UUID first = createAndPublishQuestion(token, "Jonah");
        UUID second = createAndPublishQuestion(token, "Moses");
        UUID third = createAndPublishQuestion(token, "Noah");

        // list mine: the new draft appears, with a zero question count
        mockMvc.perform(get("/api/v1/quizzes/mine")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='%s')].questionCount".formatted(quizId)).value(0))
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // attach all three, appended in order
        attachQuestion(token, quizId, first).andExpect(jsonPath("$.questionIds.length()").value(1));
        attachQuestion(token, quizId, second).andExpect(jsonPath("$.questionIds.length()").value(2));
        JsonNode afterAttach = readJson(attachQuestion(token, quizId, third)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionIds.length()").value(3))
                .andReturn().getResponse().getContentAsString());
        assertThat(textList(afterAttach.get("questionIds")))
                .containsExactly(first.toString(), second.toString(), third.toString());

        // reorder: third, first, second
        JsonNode reordered = readJson(mockMvc.perform(patch("/api/v1/quizzes/" + quizId + "/questions/order")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionIds": ["%s", "%s", "%s"]}
                                """.formatted(third, first, second)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(textList(reordered.get("questionIds")))
                .containsExactly(third.toString(), first.toString(), second.toString());

        // remove the middle one (first)
        JsonNode afterRemove = readJson(mockMvc.perform(delete(
                        "/api/v1/quizzes/" + quizId + "/questions/" + first)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(textList(afterRemove.get("questionIds")))
                .containsExactly(third.toString(), second.toString());

        // list mine now shows the real count
        mockMvc.perform(get("/api/v1/quizzes/mine")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.items[?(@.id=='%s')].questionCount".formatted(quizId)).value(2));

        // publish
        mockMvc.perform(post("/api/v1/quizzes/" + quizId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PUBLISHED"));

        // published: may still gain a question...
        attachQuestion(token, quizId, first).andExpect(status().isOk());
        // ...but never lose one, and never reorder
        mockMvc.perform(delete("/api/v1/quizzes/" + quizId + "/questions/" + second)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quiz.questions.locked"));
        mockMvc.perform(patch("/api/v1/quizzes/" + quizId + "/questions/order")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionIds": ["%s", "%s", "%s"]}
                                """.formatted(first, second, third)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quiz.questions.locked"));
    }

    @Test
    void draftAndPublishedQuestionsAreAttachableButArchivedIsNot() throws Exception {
        String token = quizMasterToken();
        String quizId = createQuiz(token, "en", "Bible Quiz");
        UUID draftQuestion = createQuestion(token, "Jonah");

        // a draft question is attachable — an author composes while still refining it
        attachQuestion(token, quizId, draftQuestion).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/questions/" + draftQuestion + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/questions/" + draftQuestion + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // archived is unavailable for new attachment (RFC-003's flagged gap, now closed)
        UUID freshQuizId = UUID.fromString(createQuiz(token, "en", "Another Quiz"));
        mockMvc.perform(post("/api/v1/quizzes/" + freshQuizId + "/questions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionId": "%s"}
                                """.formatted(draftQuestion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quiz.question.not-attachable"));
    }

    @Test
    void attachingSomeoneElsesQuestionIsNotFound() throws Exception {
        String owner = quizMasterToken();
        String stranger = quizMasterToken();
        String quizId = createQuiz(owner, "en", "Bible Quiz");
        UUID strangersQuestion = createAndPublishQuestion(stranger, "Jonah");

        attachQuestion(owner, quizId, strangersQuestion)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("question.not-found"));
    }

    @Test
    void duplicateAttachmentIsRejected() throws Exception {
        String token = quizMasterToken();
        String quizId = createQuiz(token, "en", "Bible Quiz");
        UUID question = createAndPublishQuestion(token, "Jonah");

        attachQuestion(token, quizId, question).andExpect(status().isOk());
        attachQuestion(token, quizId, question)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("quiz.question.duplicate"));
    }

    @Test
    void reorderRejectsAnythingOtherThanTheCurrentQuestionSet() throws Exception {
        String token = quizMasterToken();
        String quizId = createQuiz(token, "en", "Bible Quiz");
        UUID first = createAndPublishQuestion(token, "Jonah");
        UUID second = createAndPublishQuestion(token, "Moses");
        attachQuestion(token, quizId, first);
        attachQuestion(token, quizId, second);

        mockMvc.perform(patch("/api/v1/quizzes/" + quizId + "/questions/order")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionIds": ["%s"]}
                                """.formatted(first)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.invalid"));

        mockMvc.perform(patch("/api/v1/quizzes/" + quizId + "/questions/order")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"questionIds": ["%s", "%s"]}
                                """.formatted(first, first)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void myQuizzesIsFilterableAndSearchable() throws Exception {
        String token = quizMasterToken();
        String draftId = createQuiz(token, "en", "Draft Quiz Alpha");
        String publishedId = createQuiz(token, "en", "Published Quiz Beta");
        attachQuestion(token, publishedId, createAndPublishQuestion(token, "Jonah"));
        mockMvc.perform(post("/api/v1/quizzes/" + publishedId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/quizzes/mine").param("state", "DRAFT")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='%s')]".formatted(draftId)).exists())
                .andExpect(jsonPath("$.items[?(@.id=='%s')]".formatted(publishedId)).doesNotExist());

        mockMvc.perform(get("/api/v1/quizzes/mine").param("search", "beta")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id=='%s')]".formatted(publishedId)).exists())
                .andExpect(jsonPath("$.items[?(@.id=='%s')]".formatted(draftId)).doesNotExist());

        mockMvc.perform(get("/api/v1/quizzes/mine").param("sort", "title")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.invalid"));
    }

    @Test
    void myQuizzesNeverIncludesAnotherOwnersQuizzes() throws Exception {
        String owner = quizMasterToken();
        String stranger = quizMasterToken();
        createQuiz(owner, "en", "Owner's Quiz");

        mockMvc.perform(get("/api/v1/quizzes/mine")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void questionLibraryIsFilterableAndOwnerScoped() throws Exception {
        String owner = quizMasterToken();
        String stranger = quizMasterToken();
        UUID easyEnglish = createAndPublishQuestion(owner, "Jonah");
        UUID hardDraft = createQuestionWithDifficulty(owner, "Moses", "HARD");
        createAndPublishQuestion(stranger, "Someone else's question");

        mockMvc.perform(get("/api/v1/questions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/questions").param("difficulty", "HARD")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(hardDraft.toString()));

        mockMvc.perform(get("/api/v1/questions").param("state", "PUBLISHED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(easyEnglish.toString()));

        mockMvc.perform(get("/api/v1/questions").param("search", "jonah")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(easyEnglish.toString()));
    }

    @Test
    void openApiDocumentsTheCompositionEndpoints() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(body).get("paths");

        assertThat(paths.has("/api/v1/quizzes/mine")).isTrue();
        assertThat(paths.has("/api/v1/questions")).isTrue();
        assertThat(paths.has("/api/v1/quizzes/{quizId}/questions")).isTrue();
        assertThat(paths.has("/api/v1/quizzes/{quizId}/questions/{questionId}")).isTrue();
        assertThat(paths.has("/api/v1/quizzes/{quizId}/questions/order")).isTrue();
    }

    // --- helpers -------------------------------------------------------------

    private List<String> textList(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    private String createQuiz(String token, String language, String title) throws Exception {
        JsonNode quiz = readJson(mockMvc.perform(post("/api/v1/quizzes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "defaultLanguage": "%s",
                                  "localization": {"languageCode": "%s", "title": "%s", "description": null}
                                }
                                """.formatted(language, language, title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return quiz.get("id").asText();
    }

    private UUID createQuestion(String token, String title) throws Exception {
        return createQuestionWithDifficulty(token, title, "EASY");
    }

    private UUID createQuestionWithDifficulty(String token, String title, String difficulty) throws Exception {
        JsonNode question = readJson(mockMvc.perform(post("/api/v1/questions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "defaultLanguage": "en",
                                  "questionType": "TRUE_FALSE",
                                  "difficulty": "%s",
                                  "localization": {"title": "%s", "prompt": "%s?", "explanation": null},
                                  "options": [
                                    {"text": "True", "correct": true, "displayOrder": 1},
                                    {"text": "False", "correct": false, "displayOrder": 2}
                                  ]
                                }
                                """.formatted(difficulty, title, title)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        return UUID.fromString(question.get("id").asText());
    }

    private UUID createAndPublishQuestion(String token, String title) throws Exception {
        UUID questionId = createQuestion(token, title);
        mockMvc.perform(post("/api/v1/questions/" + questionId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        return questionId;
    }

    private ResultActions attachQuestion(
            String token, String quizId, UUID questionId) throws Exception {
        return mockMvc.perform(post("/api/v1/quizzes/" + quizId + "/questions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"questionId": "%s"}
                        """.formatted(questionId)));
    }

    private JsonNode readJson(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private String quizMasterToken() {
        // Roles are durable (Phase 3): the grant must be persisted — the
        // token claim alone no longer authorizes anything.
        Identity identity = Identity.registered();
        identity.grantRole(Role.QUIZ_MASTER);
        identityRepository.save(identity);
        IdentitySession session = identitySessionRepository.save(
                IdentitySession.start(identity.getId(), "JUnit", "127.0.0.1", null));
        return tokenGenerator.generate(identity.getId(), session.getId(), IdentityType.REGISTERED,
                Set.of(Role.USER, Role.QUIZ_MASTER)).token();
    }
}
