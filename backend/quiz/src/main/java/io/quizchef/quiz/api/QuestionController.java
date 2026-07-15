package io.quizchef.quiz.api;

import io.quizchef.common.api.ApiError;
import io.quizchef.identity.domain.CurrentUserProvider;
import io.quizchef.quiz.application.ArchiveQuestionApplicationService;
import io.quizchef.quiz.application.ArchiveQuestionCommand;
import io.quizchef.quiz.application.CreateQuestionApplicationService;
import io.quizchef.quiz.application.PublishQuestionApplicationService;
import io.quizchef.quiz.application.PublishQuestionCommand;
import io.quizchef.quiz.application.QuestionQueryService;
import io.quizchef.quiz.application.QuestionView;
import io.quizchef.quiz.application.UpdateQuestionApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Question library endpoints: validate, delegate, respond. Authorization
 * and ownership decisions live in the application services — never here.
 */
@RestController
@RequestMapping("/api/v1/questions")
@Tag(name = "Questions", description = "The reusable multilingual question library: author once, use in many quizzes")
public class QuestionController {

    private final CreateQuestionApplicationService createQuestionApplicationService;
    private final UpdateQuestionApplicationService updateQuestionApplicationService;
    private final PublishQuestionApplicationService publishQuestionApplicationService;
    private final ArchiveQuestionApplicationService archiveQuestionApplicationService;
    private final QuestionQueryService questionQueryService;
    private final CurrentUserProvider currentUserProvider;

    public QuestionController(CreateQuestionApplicationService createQuestionApplicationService,
                              UpdateQuestionApplicationService updateQuestionApplicationService,
                              PublishQuestionApplicationService publishQuestionApplicationService,
                              ArchiveQuestionApplicationService archiveQuestionApplicationService,
                              QuestionQueryService questionQueryService,
                              CurrentUserProvider currentUserProvider) {
        this.createQuestionApplicationService = createQuestionApplicationService;
        this.updateQuestionApplicationService = updateQuestionApplicationService;
        this.publishQuestionApplicationService = publishQuestionApplicationService;
        this.archiveQuestionApplicationService = archiveQuestionApplicationService;
        this.questionQueryService = questionQueryService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    @Operation(
            summary = "Create a draft question",
            description = "Creates a draft owned by the caller, authored in its default language. "
                    + "Option ids are assigned by the server and returned for later translation. "
                    + "Requires QUIZ_CREATE.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Draft created; Location points to the question"),
            @ApiResponse(responseCode = "400", description = "Validation failed (unknown language tag, "
                    + "option rules of the question type violated, blank texts)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but lacking QUIZ_CREATE",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<QuestionResponse> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
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
                        {"book": "Exodus", "chapter": 3, "verseStart": 1, "verseEnd": 10}
                      ],
                      "tags": ["exodus", "moses"]
                    }
                    """)))
            @Valid @RequestBody CreateQuestionRequest request) {
        QuestionView view = createQuestionApplicationService.create(
                currentUserProvider.currentUser(), request.toCommand());
        return ResponseEntity
                .created(URI.create("/api/v1/questions/" + view.id()))
                .body(QuestionResponse.from(view));
    }

    @GetMapping("/{questionId}")
    @Operation(
            summary = "Read a question",
            description = "Metadata, options, every localization with its option texts, scripture and "
                    + "media references, and tags. Quiz references are absent — questions do not know "
                    + "where they are used. Requires QUIZ_VIEW; questions are visible to their owner only.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The question"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but lacking QUIZ_VIEW",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown question, or another owner's question",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public QuestionResponse get(@PathVariable UUID questionId) {
        return QuestionResponse.from(
                questionQueryService.question(currentUserProvider.currentUser(), questionId));
    }

    @PutMapping("/{questionId}")
    @Operation(
            summary = "Update a draft question",
            description = "Owner only, requires QUIZ_EDIT. A true PUT: every field is the complete new "
                    + "value; the localizations must include the default language, and options keep "
                    + "their ids to preserve translations. Published and archived questions are "
                    + "immutable. The version must be the one last read.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated question with its new version"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but lacking QUIZ_EDIT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown question, or another owner's question",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Stale version (question.concurrent-modification), "
                    + "published question (question.content.locked), or archived question (question.archived)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public QuestionResponse update(@PathVariable UUID questionId,
                                   @Valid @RequestBody UpdateQuestionRequest request) {
        return QuestionResponse.from(updateQuestionApplicationService.update(
                currentUserProvider.currentUser(), request.toCommand(questionId)));
    }

    @PostMapping("/{questionId}/publish")
    @Operation(
            summary = "Publish a draft question",
            description = "Owner only, requires QUIZ_EDIT. Publishing freezes the question — quizzes may "
                    + "rely on it from now on. The localization invariants hold by construction: the "
                    + "default language and complete option texts per stored language always exist.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The question, now PUBLISHED"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but lacking QUIZ_EDIT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown question, or another owner's question",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Not publishable (question.not-publishable) "
                    + "or archived (question.archived)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public QuestionResponse publish(@PathVariable UUID questionId) {
        return QuestionResponse.from(publishQuestionApplicationService.publish(
                currentUserProvider.currentUser(), new PublishQuestionCommand(questionId)));
    }

    @PostMapping("/{questionId}/archive")
    @Operation(
            summary = "Archive a published question",
            description = "Owner only, requires QUIZ_EDIT. Archived questions are unavailable for new "
                    + "quizzes while existing published quizzes continue functioning. Archiving is "
                    + "terminal; questions are retained, never deleted.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The question, now ARCHIVED"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but lacking QUIZ_EDIT",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown question, or another owner's question",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Not archivable from the current state "
                    + "(question.not-archivable, question.archived)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public QuestionResponse archive(@PathVariable UUID questionId) {
        return QuestionResponse.from(archiveQuestionApplicationService.archive(
                currentUserProvider.currentUser(), new ArchiveQuestionCommand(questionId)));
    }
}
