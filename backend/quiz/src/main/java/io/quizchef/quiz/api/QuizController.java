package io.quizchef.quiz.api;

import io.quizchef.common.api.ApiError;
import io.quizchef.identity.domain.CurrentUserProvider;
import io.quizchef.quiz.application.AddQuestionToQuizApplicationService;
import io.quizchef.quiz.application.ArchiveQuizApplicationService;
import io.quizchef.quiz.application.ArchiveQuizCommand;
import io.quizchef.quiz.application.CreateQuizApplicationService;
import io.quizchef.quiz.application.PublishQuizApplicationService;
import io.quizchef.quiz.application.PublishQuizCommand;
import io.quizchef.quiz.application.QuizQueryService;
import io.quizchef.quiz.application.QuizSearchQuery;
import io.quizchef.quiz.application.QuizView;
import io.quizchef.quiz.application.RemoveQuestionFromQuizApplicationService;
import io.quizchef.quiz.application.RemoveQuestionFromQuizCommand;
import io.quizchef.quiz.application.ReorderQuizQuestionsApplicationService;
import io.quizchef.quiz.application.UpdateQuizApplicationService;
import io.quizchef.quiz.domain.QuizState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final AddQuestionToQuizApplicationService addQuestionToQuizApplicationService;
    private final RemoveQuestionFromQuizApplicationService removeQuestionFromQuizApplicationService;
    private final ReorderQuizQuestionsApplicationService reorderQuizQuestionsApplicationService;
    private final QuizQueryService quizQueryService;
    private final CurrentUserProvider currentUserProvider;

    public QuizController(CreateQuizApplicationService createQuizApplicationService,
                          UpdateQuizApplicationService updateQuizApplicationService,
                          PublishQuizApplicationService publishQuizApplicationService,
                          ArchiveQuizApplicationService archiveQuizApplicationService,
                          AddQuestionToQuizApplicationService addQuestionToQuizApplicationService,
                          RemoveQuestionFromQuizApplicationService removeQuestionFromQuizApplicationService,
                          ReorderQuizQuestionsApplicationService reorderQuizQuestionsApplicationService,
                          QuizQueryService quizQueryService,
                          CurrentUserProvider currentUserProvider) {
        this.createQuizApplicationService = createQuizApplicationService;
        this.updateQuizApplicationService = updateQuizApplicationService;
        this.publishQuizApplicationService = publishQuizApplicationService;
        this.archiveQuizApplicationService = archiveQuizApplicationService;
        this.addQuestionToQuizApplicationService = addQuestionToQuizApplicationService;
        this.removeQuestionFromQuizApplicationService = removeQuestionFromQuizApplicationService;
        this.reorderQuizQuestionsApplicationService = reorderQuizQuestionsApplicationService;
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

    @GetMapping("/mine")
    @Operation(
            summary = "List the caller's own quizzes",
            description = "\"My Quizzes\": every state, filterable and paged. There is no endpoint to "
                    + "list quizzes across authors — this is deliberately owner-scoped only. Sortable "
                    + "only by updatedAt, createdAt, or state (content lives in per-language "
                    + "localizations, not a root column). Requires QUIZ_VIEW.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of the caller's quizzes"),
            @ApiResponse(responseCode = "400", description = "Unsupported sort property",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but lacking QUIZ_VIEW",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public QuizPageResponse mine(
            @RequestParam(required = false) QuizState state,
            @Parameter(description = "Case-insensitive match against any localization's title")
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return QuizPageResponse.from(quizQueryService
                .mine(currentUserProvider.currentUser(), new QuizSearchQuery(state, search), pageable)
                .map(QuizSummaryResponse::from));
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

    @PostMapping("/{quizId}/questions")
    @Operation(
            summary = "Attach a question to the quiz",
            description = "Owner only, requires QUIZ_EDIT. Attaches one of the caller's own questions — "
                    + "draft or published, refined further after attaching if still draft; only an "
                    + "archived question is rejected, retired from new use. Allowed while the quiz is "
                    + "DRAFT or PUBLISHED (a published quiz may gain questions, never lose them); "
                    + "appended to the end of the ordering. Publishing the quiz itself still requires "
                    + "every attached question to carry the quiz's default language.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The quiz, with the question attached"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_EDIT, or not the quiz owner",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown quiz or question, or one owned by "
                    + "someone else",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Archived quiz (quiz.archived), duplicate "
                    + "attachment (quiz.question.duplicate), or the question is archived "
                    + "(quiz.question.not-attachable)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public QuizResponse addQuestion(@PathVariable UUID quizId,
                                    @Valid @RequestBody AddQuestionRequest request) {
        return QuizResponse.from(addQuestionToQuizApplicationService.add(
                currentUserProvider.currentUser(), request.toCommand(quizId)));
    }

    @DeleteMapping("/{quizId}/questions/{questionId}")
    @Operation(
            summary = "Detach a question from the quiz",
            description = "Owner only, requires QUIZ_EDIT. Draft only — a published quiz may gain "
                    + "questions but never lose them. No quiz deletion; this only removes the "
                    + "composition reference.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The quiz, with the question detached"),
            @ApiResponse(responseCode = "400", description = "The question is not part of this quiz",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_EDIT, or not the quiz owner",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown quiz, or another owner's private quiz",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Published or archived quiz "
                    + "(quiz.questions.locked, quiz.archived)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public QuizResponse removeQuestion(@PathVariable UUID quizId, @PathVariable UUID questionId) {
        return QuizResponse.from(removeQuestionFromQuizApplicationService.remove(
                currentUserProvider.currentUser(), new RemoveQuestionFromQuizCommand(quizId, questionId)));
    }

    @PatchMapping("/{quizId}/questions/order")
    @Operation(
            summary = "Reorder the quiz's questions",
            description = "Owner only, requires QUIZ_EDIT. Draft only. The request must name exactly the "
                    + "quiz's current questions, each once, in their new order; positions are "
                    + "reassigned 1..n accordingly. Persisted atomically.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The quiz, questions in their new order"),
            @ApiResponse(responseCode = "400", description = "The list is not exactly the quiz's current "
                    + "questions (missing, extra, or duplicate ids)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_EDIT, or not the quiz owner",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown quiz, or another owner's private quiz",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Published or archived quiz "
                    + "(quiz.questions.locked, quiz.archived)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public QuizResponse reorderQuestions(@PathVariable UUID quizId,
                                         @Valid @RequestBody ReorderQuestionsRequest request) {
        return QuizResponse.from(reorderQuizQuestionsApplicationService.reorder(
                currentUserProvider.currentUser(), request.toCommand(quizId)));
    }
}
