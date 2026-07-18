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
import io.quizchef.quiz.domain.event.QuestionArchivedEvent;
import io.quizchef.quiz.domain.event.QuestionPublishedEvent;
import io.quizchef.quiz.infrastructure.persistence.TagRepository;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end question library: authoring a reusable multilingual question
 * over HTTP, its lifecycle, tags, optimistic locking, ownership, and
 * authorization.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class QuestionLibraryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration(proxyBeanMethods = false)
    static class EventCapture {

        final List<QuestionPublishedEvent> published = new CopyOnWriteArrayList<>();
        final List<QuestionArchivedEvent> archived = new CopyOnWriteArrayList<>();

        @EventListener
        void on(QuestionPublishedEvent event) {
            published.add(event);
        }

        @EventListener
        void on(QuestionArchivedEvent event) {
            archived.add(event);
        }
    }

    private static final String CREATE_QUESTION_JSON = """
            {
              "defaultLanguage": "en",
              "questionType": "SINGLE_CHOICE",
              "difficulty": "EASY",
              "localization": {
                "title": "Exodus leader",
                "prompt": "Who led Israel out of Egypt?",
                "explanation": "See Exodus 3."
              },
              "options": [
                {"text": "Moses", "correct": true, "displayOrder": 1},
                {"text": "Aaron", "correct": false, "displayOrder": 2}
              ],
              "bibleReferences": [
                {"book": "Exodus", "chapter": 3, "verseStart": 1, "verseEnd": 10, "translation": "ESV"}
              ],
              "tags": ["Exodus", "moses"]
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
    private TagRepository tagRepository;

    @Autowired
    private EventCapture eventCapture;

    @BeforeEach
    void resetCapturedEvents() {
        eventCapture.published.clear();
        eventCapture.archived.clear();
    }

    @Test
    void fullQuestionAuthoringWorkflow() throws Exception {
        String token = quizMasterToken();

        // create a draft, tagged, with a scripture reference
        JsonNode question = readJson(mockMvc.perform(post("/api/v1/questions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_QUESTION_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.startsWith("/api/v1/questions/")))
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.defaultLanguage").value("en"))
                .andExpect(jsonPath("$.options.length()").value(2))
                .andExpect(jsonPath("$.bibleReferences[0].book").value("Exodus"))
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.tags[0].name").value("exodus"))
                .andReturn().getResponse().getContentAsString());
        String questionId = question.get("id").asText();

        // the draft is readable with its English content and option texts
        mockMvc.perform(get("/api/v1/questions/" + questionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.localizations.length()").value(1))
                .andExpect(jsonPath("$.localizations[0].languageCode").value("en"))
                .andExpect(jsonPath("$.localizations[0].optionTexts[0].text").value("Moses"));

        // add a Kannada translation covering every option
        JsonNode translated = readJson(mockMvc.perform(put("/api/v1/questions/" + questionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateWithKannada(question, "MEDIUM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.difficulty").value("MEDIUM"))
                .andExpect(jsonPath("$.localizations.length()").value(2))
                .andReturn().getResponse().getContentAsString());
        assertThat(translated.get("version").asLong()).isGreaterThan(0);

        // publish: the question freezes and becomes reusable
        mockMvc.perform(post("/api/v1/questions/" + questionId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PUBLISHED"));
        assertThat(eventCapture.published).singleElement()
                .satisfies(event -> assertThat(event.questionId()).hasToString(questionId));

        // published questions are immutable
        mockMvc.perform(put("/api/v1/questions/" + questionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateWithKannada(readCurrent(questionId, token), "HARD")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("question.content.locked"));

        // archive: retained, unavailable for new quizzes
        mockMvc.perform(post("/api/v1/questions/" + questionId + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ARCHIVED"));
        assertThat(eventCapture.archived).singleElement()
                .satisfies(event -> assertThat(event.questionId()).hasToString(questionId));

        // still readable afterwards, with everything intact
        mockMvc.perform(get("/api/v1/questions/" + questionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ARCHIVED"))
                .andExpect(jsonPath("$.localizations.length()").value(2))
                .andExpect(jsonPath("$.tags.length()").value(2));
    }

    @Test
    void multilingualContentRoundTripsThroughTheApi() throws Exception {
        String token = quizMasterToken();
        JsonNode question = createQuestion(token);
        String questionId = question.get("id").asText();

        mockMvc.perform(put("/api/v1/questions/" + questionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateWithKannada(question, "EASY")))
                .andExpect(status().isOk());

        JsonNode reloaded = readCurrent(questionId, token);
        JsonNode kannada = localizationOf(reloaded, "kn");
        assertThat(kannada.get("prompt").asText()).isEqualTo("ಇಸ್ರಾಯೇಲನ್ನು ಈಜಿಪ್ಟಿನಿಂದ ಹೊರತಂದವರು ಯಾರು?");
        assertThat(kannada.get("optionTexts").get(0).get("text").asText()).isEqualTo("ಮೋಶೆ");

        // structure is shared across languages: same option ids, correctness untouched
        JsonNode english = localizationOf(reloaded, "en");
        assertThat(kannada.get("optionTexts").get(0).get("optionId").asText())
                .isEqualTo(english.get("optionTexts").get(0).get("optionId").asText());
        assertThat(reloaded.get("options").get(0).get("correct").asBoolean()).isTrue();
    }

    @Test
    void incompleteTranslationsAreRejected() throws Exception {
        String token = quizMasterToken();
        JsonNode question = createQuestion(token);
        String firstOptionId = question.get("options").get(0).get("id").asText();

        String kannadaMissingAnOption = """
                {
                  "version": %d,
                  "difficulty": "EASY",
                  "options": %s,
                  "localizations": [
                    %s,
                    {
                      "languageCode": "kn",
                      "title": "ನಾಯಕ",
                      "prompt": "ಯಾರು?",
                      "explanation": null,
                      "optionTexts": [{"optionId": "%s", "text": "ಮೋಶೆ"}]
                    }
                  ],
                  "bibleReferences": [],
                  "mediaReferences": [],
                  "tags": []
                }
                """.formatted(question.get("version").asLong(), optionsJson(question),
                englishLocalizationJson(question), firstOptionId);

        mockMvc.perform(put("/api/v1/questions/" + question.get("id").asText())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(kannadaMissingAnOption))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.invalid"));
    }

    @Test
    void tagsAreSharedAndResolvedByNormalizedName() throws Exception {
        String token = quizMasterToken();

        JsonNode first = createQuestion(token);
        JsonNode second = readJson(mockMvc.perform(post("/api/v1/questions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_QUESTION_JSON.replace("\"tags\": [\"Exodus\", \"moses\"]",
                                "\"tags\": [\"  EXODUS  \", \"red sea\"]")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        // "Exodus", "  EXODUS  " → the same tag aggregate, one row
        String firstExodusId = tagIdOf(first, "exodus");
        String secondExodusId = tagIdOf(second, "exodus");
        assertThat(firstExodusId).isEqualTo(secondExodusId);
        assertThat(tagRepository.findByName("exodus")).isPresent();
        assertThat(tagRepository.findAll())
                .extracting(io.quizchef.quiz.domain.Tag::getName)
                .containsExactlyInAnyOrder("exodus", "moses", "red sea");
    }

    @Test
    void staleVersionsAreRejectedInsteadOfSilentlyOverwriting() throws Exception {
        String token = quizMasterToken();
        JsonNode question = createQuestion(token);
        String questionId = question.get("id").asText();

        // author A saves against version 0
        mockMvc.perform(put("/api/v1/questions/" + questionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateWithKannada(question, "HARD")))
                .andExpect(status().isOk());

        // author B, still holding version 0, loses
        mockMvc.perform(put("/api/v1/questions/" + questionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateWithKannada(question, "MEDIUM")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("question.concurrent-modification"));
    }

    @Test
    void draftsCannotBeArchived() throws Exception {
        String token = quizMasterToken();
        JsonNode question = createQuestion(token);

        mockMvc.perform(post("/api/v1/questions/" + question.get("id").asText() + "/archive")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("question.not-archivable"));
    }

    @Test
    void anonymousCallersAreUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_QUESTION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.unauthorized"));
    }

    @Test
    void usersWithoutQuizCreateAreForbidden() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(post("/api/v1/questions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_QUESTION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.permission.denied"));
    }

    @Test
    void questionsAreVisibleAndEditableByTheirOwnerOnly() throws Exception {
        String ownerToken = quizMasterToken();
        String strangerToken = quizMasterToken();
        JsonNode question = createQuestion(ownerToken);
        String questionId = question.get("id").asText();

        mockMvc.perform(get("/api/v1/questions/" + questionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("question.not-found"));
        mockMvc.perform(put("/api/v1/questions/" + questionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateWithKannada(question, "HARD")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/questions/" + questionId + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isNotFound());

        // the owner is the caller's identity, never client-supplied
        mockMvc.perform(get("/api/v1/questions/" + questionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerIdentityId").isNotEmpty());
    }

    @Test
    void openApiDocumentsTheQuestionEndpoints() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = objectMapper.readTree(body).get("paths");

        assertThat(paths.has("/api/v1/questions")).isTrue();
        assertThat(paths.has("/api/v1/questions/{questionId}")).isTrue();
        assertThat(paths.has("/api/v1/questions/{questionId}/publish")).isTrue();
        assertThat(paths.has("/api/v1/questions/{questionId}/archive")).isTrue();
        assertThat(paths.at("/~1api~1v1~1questions/post/security/0/bearerAuth").isMissingNode())
                .as("create question documents bearer authentication")
                .isFalse();
        assertThat(paths.at("/~1api~1v1~1questions~1{questionId}/put/responses/409").isMissingNode())
                .as("update question documents the conflict contract")
                .isFalse();
    }

    private JsonNode createQuestion(String token) throws Exception {
        return readJson(mockMvc.perform(post("/api/v1/questions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_QUESTION_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode readCurrent(String questionId, String token) throws Exception {
        return readJson(mockMvc.perform(get("/api/v1/questions/" + questionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    /**
     * The full editable representation with a Kannada translation added —
     * PUT replaces everything, so the English content rides along.
     */
    private String updateWithKannada(JsonNode question, String difficulty) throws Exception {
        String firstOptionId = question.get("options").get(0).get("id").asText();
        String secondOptionId = question.get("options").get(1).get("id").asText();
        return """
                {
                  "version": %d,
                  "difficulty": "%s",
                  "options": %s,
                  "localizations": [
                    %s,
                    {
                      "languageCode": "kn",
                      "title": "ವಿಮೋಚನೆಯ ನಾಯಕ",
                      "prompt": "ಇಸ್ರಾಯೇಲನ್ನು ಈಜಿಪ್ಟಿನಿಂದ ಹೊರತಂದವರು ಯಾರು?",
                      "explanation": null,
                      "optionTexts": [
                        {"optionId": "%s", "text": "ಮೋಶೆ"},
                        {"optionId": "%s", "text": "ಆರೋನ್"}
                      ]
                    }
                  ],
                  "bibleReferences": [],
                  "mediaReferences": [],
                  "tags": ["exodus", "moses"]
                }
                """.formatted(question.get("version").asLong(), difficulty, optionsJson(question),
                englishLocalizationJson(question), firstOptionId, secondOptionId);
    }

    private String optionsJson(JsonNode question) throws Exception {
        return objectMapper.writeValueAsString(question.get("options"));
    }

    private String englishLocalizationJson(JsonNode question) throws Exception {
        return objectMapper.writeValueAsString(localizationOf(question, "en"));
    }

    private static JsonNode localizationOf(JsonNode question, String language) {
        for (JsonNode localization : question.get("localizations")) {
            if (localization.get("languageCode").asText().equals(language)) {
                return localization;
            }
        }
        throw new AssertionError("no localization for " + language);
    }

    private static String tagIdOf(JsonNode question, String name) {
        for (JsonNode tag : question.get("tags")) {
            if (tag.get("name").asText().equals(name)) {
                return tag.get("id").asText();
            }
        }
        throw new AssertionError("no tag named " + name);
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
}
