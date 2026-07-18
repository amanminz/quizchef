package io.quizchef.session.api;

import io.quizchef.common.api.ApiError;
import io.quizchef.identity.domain.CurrentUserProvider;
import io.quizchef.session.application.AdvanceQuestionApplicationService;
import io.quizchef.session.application.CloseQuestionApplicationService;
import io.quizchef.session.application.CurrentQuestionQueryService;
import io.quizchef.session.application.SessionResultsQueryService;
import io.quizchef.session.application.RevealAnswerApplicationService;
import io.quizchef.session.application.ShowLeaderboardApplicationService;
import io.quizchef.session.application.StartQuestionApplicationService;
import io.quizchef.session.application.SubmitAnswerApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Live gameplay endpoints: host progression commands and the participant
 * answer. Validate, delegate, respond — the server owns all game state
 * (ADR-006); nothing is computed here.
 */
@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Gameplay", description = "Running a live session: open/close/reveal questions, leaderboard, answers")
public class GameplayController {

    private final StartQuestionApplicationService startQuestionApplicationService;
    private final CloseQuestionApplicationService closeQuestionApplicationService;
    private final RevealAnswerApplicationService revealAnswerApplicationService;
    private final ShowLeaderboardApplicationService showLeaderboardApplicationService;
    private final AdvanceQuestionApplicationService advanceQuestionApplicationService;
    private final SubmitAnswerApplicationService submitAnswerApplicationService;
    private final CurrentQuestionQueryService currentQuestionQueryService;
    private final SessionResultsQueryService sessionResultsQueryService;
    private final CurrentUserProvider currentUserProvider;

    public GameplayController(StartQuestionApplicationService startQuestionApplicationService,
                             CloseQuestionApplicationService closeQuestionApplicationService,
                             RevealAnswerApplicationService revealAnswerApplicationService,
                             ShowLeaderboardApplicationService showLeaderboardApplicationService,
                             AdvanceQuestionApplicationService advanceQuestionApplicationService,
                             SubmitAnswerApplicationService submitAnswerApplicationService,
                             CurrentQuestionQueryService currentQuestionQueryService,
                             SessionResultsQueryService sessionResultsQueryService,
                             CurrentUserProvider currentUserProvider) {
        this.startQuestionApplicationService = startQuestionApplicationService;
        this.closeQuestionApplicationService = closeQuestionApplicationService;
        this.revealAnswerApplicationService = revealAnswerApplicationService;
        this.showLeaderboardApplicationService = showLeaderboardApplicationService;
        this.advanceQuestionApplicationService = advanceQuestionApplicationService;
        this.submitAnswerApplicationService = submitAnswerApplicationService;
        this.currentQuestionQueryService = currentQuestionQueryService;
        this.sessionResultsQueryService = sessionResultsQueryService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/{id}/results")
    @Operation(
            summary = "Read the session's full standings (host only)",
            description = "The ranked standings with the counts a results screen frames them with — "
                    + "interim between questions and final after FINISHED share this one read. Host "
                    + "only since the live-event privacy split: every name, score, and rank is the "
                    + "host's projection; a participant device reads its own row through the "
                    + "personal-result endpoint instead. Phase-gated for the same ADR-006 reason "
                    + "correctness is: readable only once the current question's answer is revealed "
                    + "(or the leaderboard is showing, or the session has finished).",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The current standings"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_HOST, or not the host",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown session",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Results not readable yet "
                    + "(session.results.not-available)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public SessionResultsResponse results(@PathVariable UUID id) {
        return SessionResultsResponse.from(
                sessionResultsQueryService.results(currentUserProvider.currentUser(), id));
    }

    @GetMapping("/{id}/participants/{participantId}/result")
    @Operation(
            summary = "Read one participant's own result",
            description = "The participant's rank, score, and the counts that frame them — and "
                    + "nothing about anyone else. Open like the summary and current-question reads: "
                    + "the audience is anonymous guests, and the unguessable session and participant "
                    + "ids gate it — the same trust answer submission places in the participant id. "
                    + "Phase-gated exactly like the host standings read.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The participant's own result"),
            @ApiResponse(responseCode = "404", description = "Unknown session, or no such participant "
                    + "in it (session.participant.not-found)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Results not readable yet "
                    + "(session.results.not-available)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ParticipantResultResponse participantResult(@PathVariable UUID id,
                                                       @PathVariable UUID participantId) {
        return ParticipantResultResponse.from(
                sessionResultsQueryService.personalResult(id, participantId));
    }

    @GetMapping("/{id}/questions/current")
    @Operation(
            summary = "Read the question in play",
            description = "The current question's participant-safe content: prompt and options in "
                    + "every authored language, position in the quiz, phase, and the server clock's "
                    + "remaining time. Open by session id — the players it serves are anonymous "
                    + "guests, and the unguessable id (like the summary endpoint) is the gate. "
                    + "Options never carry correctness; correctOptionIds appears only once the "
                    + "phase has revealed it, exactly like the answer.revealed event.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The question in play"),
            @ApiResponse(responseCode = "404", description = "Unknown session",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "No question is in play "
                    + "(session.no-current-question)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public CurrentQuestionResponse currentQuestion(@PathVariable UUID id) {
        return CurrentQuestionResponse.from(currentQuestionQueryService.currentQuestion(id));
    }

    @PostMapping("/{id}/questions/start")
    @Operation(
            summary = "Open the first question",
            description = "Host only. Opens the quiz's first question and arms the server timer. The "
                    + "engine chooses the question; the host drives the pace.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The session, now QUESTION_OPEN"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_HOST, or not the host",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown session",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Not startable here (session.invalid-transition, "
                    + "session.not-startable)", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public SessionSummaryResponse startQuestion(@PathVariable UUID id) {
        return SessionSummaryResponse.from(startQuestionApplicationService.start(
                currentUserProvider.currentUser(), id));
    }

    @PostMapping("/{id}/questions/close")
    @Operation(
            summary = "Close the current question",
            description = "Host only. Stops accepting answers early; the timer would otherwise close it.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The session, now QUESTION_CLOSED"),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_HOST, or not the host",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "No question is open (session.invalid-transition)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public SessionSummaryResponse closeQuestion(@PathVariable UUID id) {
        return SessionSummaryResponse.from(closeQuestionApplicationService.close(
                currentUserProvider.currentUser(), id));
    }

    @PostMapping("/{id}/questions/reveal")
    @Operation(
            summary = "Reveal the correct answer",
            description = "Host only. Broadcasts the correct options for the closed question.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The session, now ANSWER_REVEALED"),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_HOST, or not the host",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "The question is not closed (session.invalid-transition)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public SessionSummaryResponse revealAnswer(@PathVariable UUID id) {
        return SessionSummaryResponse.from(revealAnswerApplicationService.reveal(
                currentUserProvider.currentUser(), id));
    }

    @PostMapping("/{id}/leaderboard")
    @Operation(
            summary = "Show the leaderboard",
            description = "Host only. Projects and broadcasts the standings, and returns them. The "
                    + "leaderboard is computed, never stored.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The current standings"),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_HOST, or not the host",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "The answer is not revealed yet (session.invalid-transition)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public LeaderboardResponse showLeaderboard(@PathVariable UUID id) {
        return LeaderboardResponse.from(showLeaderboardApplicationService.show(
                currentUserProvider.currentUser(), id));
    }

    @PostMapping("/{id}/questions/advance")
    @Operation(
            summary = "Advance to the next question, or finish",
            description = "Host only. From the leaderboard, opens the next question in order, or finishes "
                    + "the session when the quiz is exhausted.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "QUESTION_OPEN for the next question, or FINISHED"),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_HOST, or not the host",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Not at the leaderboard (session.invalid-transition)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public SessionSummaryResponse advanceQuestion(@PathVariable UUID id) {
        return SessionSummaryResponse.from(advanceQuestionApplicationService.advance(
                currentUserProvider.currentUser(), id));
    }

    @PostMapping("/{id}/answers")
    @Operation(
            summary = "Submit an answer",
            description = "Open to participants (anonymous-friendly). The server validates the answer, "
                    + "stamps the response time, and scores it — the response confirms receipt and "
                    + "carries no score.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Answer accepted (no score returned)"),
            @ApiResponse(responseCode = "400", description = "Validation failed (empty or invalid options)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown participant (session.participant.not-found)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Not accepted — question not open, already "
                    + "answered (session.answer.not-accepted), or not connected (session.participant.not-connected)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AnswerAcceptedResponse submitAnswer(@PathVariable UUID id,
                                               @Valid @RequestBody SubmitAnswerRequest request) {
        return AnswerAcceptedResponse.from(submitAnswerApplicationService.submit(request.toCommand()));
    }
}
