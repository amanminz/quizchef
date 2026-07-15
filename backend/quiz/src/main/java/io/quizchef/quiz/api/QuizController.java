package io.quizchef.quiz.api;

import io.quizchef.common.api.ApiError;
import io.quizchef.identity.domain.CurrentUserProvider;
import io.quizchef.quiz.application.ArchiveQuizApplicationService;
import io.quizchef.quiz.application.ArchiveQuizCommand;
import io.quizchef.quiz.application.CreateQuizApplicationService;
import io.quizchef.quiz.application.PublishQuizApplicationService;
import io.quizchef.quiz.application.PublishQuizCommand;
import io.quizchef.quiz.application.QuizQueryService;
import io.quizchef.quiz.application.QuizView;
import io.quizchef.quiz.application.UpdateQuizApplicationService;
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
 * Quiz authoring endpoints: validate, delegate, respond. Authorization and
 * ownership decisions live in the application services — never here.
 */
@RestController
@RequestMapping("/api/v1/quizzes")
@Tag(name = "Quizzes", description = "Authoring multilingual quizzes: create, edit, publish, archive")
public class QuizController {

    private final CreateQuizApplicationService createQuizApplicationService;
    private final UpdateQuizApplicationService updateQuizApplicationService;
    private final PublishQuizApplicationService publishQuizApplicationService;
    private final ArchiveQuizApplicationService archiveQuizApplicationService;
    private final QuizQueryService quizQueryService;
    private final CurrentUserProvider currentUserProvider;

    public QuizController(CreateQuizApplicationService createQuizApplicationService,
                          UpdateQuizApplicationService updateQuizApplicationService,
                          PublishQuizApplicationService publishQuizApplicationService,
                          ArchiveQuizApplicationService archiveQuizApplicationService,
                          QuizQueryService quizQueryService,
                          CurrentUserProvider currentUserProvider) {
        this.createQuizApplicationService = createQuizApplicationService;
        this.updateQuizApplicationService = updateQuizApplicationService;
        this.publishQuizApplicationService = publishQuizApplicationService;
        this.archiveQuizApplicationService = archiveQuizApplicationService;
        this.quizQueryService = quizQueryService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    @Operation(
            summary = "Create a draft quiz",
            description = "Creates a private draft owned by the caller. The localization becomes the "
                    + "quiz's default language content. Requires QUIZ_CREATE.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Draft created; Location points to the quiz"),
            @ApiResponse(responseCode = "400", description = "Validation failed (unknown language tag, blank title, settings out of range)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but lacking QUIZ_CREATE",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<QuizResponse> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(value = """
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
                    """)))
            @Valid @RequestBody CreateQuizRequest request) {
        QuizView view = createQuizApplicationService.create(
                currentUserProvider.currentUser(), request.toCommand());
        return ResponseEntity
                .created(URI.create("/api/v1/quizzes/" + view.id()))
                .body(QuizResponse.from(view));
    }

    @GetMapping("/{quizId}")
    @Operation(
            summary = "Read a quiz",
            description = "Metadata, settings, state, visibility, default language, available "
                    + "localizations, and ordered question ids. Questions are separate resources. "
                    + "Requires QUIZ_VIEW; private quizzes are visible to their owner only.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The quiz"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but lacking QUIZ_VIEW",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown quiz, or another owner's private quiz",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public QuizResponse get(@PathVariable UUID quizId) {
        return QuizResponse.from(quizQueryService.quiz(currentUserProvider.currentUser(), quizId));
    }

    @PutMapping("/{quizId}")
    @Operation(
            summary = "Update a quiz",
            description = "Owner only, requires QUIZ_EDIT. Omitted fields stay unchanged; a provided "
                    + "localization list replaces all translations. Drafts change freely; published "
                    + "quizzes accept nothing but visibility; archived quizzes are immutable. The "
                    + "version must be the one last read — stale versions are rejected.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated quiz with its new version"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_EDIT, or not the owner",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown quiz, or another owner's private quiz",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Stale version (quiz.concurrent-modification), "
                    + "content change on a published quiz (quiz.content.locked), archived quiz "
                    + "(quiz.archived), or default localization removal (quiz.localization.default-required)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public QuizResponse update(@PathVariable UUID quizId,
                               @Valid @RequestBody UpdateQuizRequest request) {
        return QuizResponse.from(updateQuizApplicationService.update(
                currentUserProvider.currentUser(), request.toCommand(quizId)));
    }

    @PostMapping("/{quizId}/publish")
    @Operation(
            summary = "Publish a draft quiz",
            description = "Owner only, requires QUIZ_EDIT. Publishing needs at least one question, and "
                    + "every attached question must be localized in the quiz's default language.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The quiz, now PUBLISHED"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_EDIT, or not the owner",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown quiz, or another owner's private quiz",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Not publishable (quiz.not-publishable) or "
                    + "archived (quiz.archived)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public QuizResponse publish(@PathVariable UUID quizId) {
        return QuizResponse.from(publishQuizApplicationService.publish(
                currentUserProvider.currentUser(), new PublishQuizCommand(quizId)));
    }

    @PostMapping("/{quizId}/archive")
    @Operation(
            summary = "Archive a published quiz",
            description = "Owner only, requires QUIZ_EDIT. Only published quizzes can be archived; "
                    + "archiving is terminal and archived quizzes are retained, never deleted.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The quiz, now ARCHIVED"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_EDIT, or not the owner",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown quiz, or another owner's private quiz",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Not archivable from the current state "
                    + "(quiz.not-archivable, quiz.archived)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public QuizResponse archive(@PathVariable UUID quizId) {
        return QuizResponse.from(archiveQuizApplicationService.archive(
                currentUserProvider.currentUser(), new ArchiveQuizCommand(quizId)));
    }
}
