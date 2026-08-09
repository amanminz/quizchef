package io.quizchef.session.api;

import io.quizchef.common.api.ApiError;
import io.quizchef.identity.domain.CurrentUserProvider;
import io.quizchef.session.application.AdvanceQuestionApplicationService;
import io.quizchef.session.application.AnswerDistributionQueryService;
import io.quizchef.session.application.AnswerProgressQueryService;
import io.quizchef.session.application.CloseQuestionApplicationService;
import io.quizchef.session.application.CurrentQuestionQueryService;
import io.quizchef.session.application.FinalStandingsQueryService;
import io.quizchef.session.application.ParticipantFinalPlacementQueryService;
import io.quizchef.session.application.ReleaseFinalResultsApplicationService;
import io.quizchef.session.application.SessionResultsQueryService;
import io.quizchef.session.application.RevealAnswerApplicationService;
import io.quizchef.session.application.ShowLeaderboardApplicationService;
import io.quizchef.session.application.ShuffleQuestionsApplicationService;
import io.quizchef.session.application.StartQuestionApplicationService;
import io.quizchef.session.application.SubmitAnswerApplicationService;
import io.quizchef.session.application.TopFiveLeaderboardQueryService;
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
    private final ShuffleQuestionsApplicationService shuffleQuestionsApplicationService;
    private final AdvanceQuestionApplicationService advanceQuestionApplicationService;
    private final SubmitAnswerApplicationService submitAnswerApplicationService;
    private final CurrentQuestionQueryService currentQuestionQueryService;
    private final SessionResultsQueryService sessionResultsQueryService;
    private final AnswerProgressQueryService answerProgressQueryService;
    private final AnswerDistributionQueryService answerDistributionQueryService;
    private final ParticipantFinalPlacementQueryService participantFinalPlacementQueryService;
    private final FinalStandingsQueryService finalStandingsQueryService;
    private final TopFiveLeaderboardQueryService topFiveLeaderboardQueryService;
    private final ReleaseFinalResultsApplicationService releaseFinalResultsApplicationService;
    private final CurrentUserProvider currentUserProvider;

    public GameplayController(StartQuestionApplicationService startQuestionApplicationService,
                             CloseQuestionApplicationService closeQuestionApplicationService,
                             RevealAnswerApplicationService revealAnswerApplicationService,
                             ShowLeaderboardApplicationService showLeaderboardApplicationService,
                             ShuffleQuestionsApplicationService shuffleQuestionsApplicationService,
                             AdvanceQuestionApplicationService advanceQuestionApplicationService,
                             SubmitAnswerApplicationService submitAnswerApplicationService,
                             CurrentQuestionQueryService currentQuestionQueryService,
                             SessionResultsQueryService sessionResultsQueryService,
                             AnswerProgressQueryService answerProgressQueryService,
                             AnswerDistributionQueryService answerDistributionQueryService,
                             ParticipantFinalPlacementQueryService participantFinalPlacementQueryService,
                             FinalStandingsQueryService finalStandingsQueryService,
                             TopFiveLeaderboardQueryService topFiveLeaderboardQueryService,
                             ReleaseFinalResultsApplicationService releaseFinalResultsApplicationService,
                             CurrentUserProvider currentUserProvider) {
        this.startQuestionApplicationService = startQuestionApplicationService;
        this.closeQuestionApplicationService = closeQuestionApplicationService;
        this.revealAnswerApplicationService = revealAnswerApplicationService;
        this.showLeaderboardApplicationService = showLeaderboardApplicationService;
        this.shuffleQuestionsApplicationService = shuffleQuestionsApplicationService;
        this.advanceQuestionApplicationService = advanceQuestionApplicationService;
        this.submitAnswerApplicationService = submitAnswerApplicationService;
        this.currentQuestionQueryService = currentQuestionQueryService;
        this.sessionResultsQueryService = sessionResultsQueryService;
        this.answerProgressQueryService = answerProgressQueryService;
        this.answerDistributionQueryService = answerDistributionQueryService;
        this.participantFinalPlacementQueryService = participantFinalPlacementQueryService;
        this.finalStandingsQueryService = finalStandingsQueryService;
        this.topFiveLeaderboardQueryService = topFiveLeaderboardQueryService;
        this.releaseFinalResultsApplicationService = releaseFinalResultsApplicationService;
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

    @GetMapping("/{id}/answer-progress")
    @Operation(
            summary = "Read the current question's answer progress (host only)",
            description = "How many participants have an accepted answer for the question in play, "
                    + "out of how many are eligible to answer right now — the authoritative counts "
                    + "behind the host's \"5 / 10 answered\". Counts only, never who; participants "
                    + "see only their own acknowledgement. Refreshed by the host on each "
                    + "answer.progress broadcast.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The current counts"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_HOST, or not the host",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown session",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "No question is in play "
                    + "(session.no-current-question)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AnswerProgressResponse answerProgress(@PathVariable UUID id) {
        return AnswerProgressResponse.from(
                answerProgressQueryService.progress(currentUserProvider.currentUser(), id));
    }

    @GetMapping("/{id}/answer-distribution")
    @Operation(
            summary = "Read the current question's answer distribution (host only)",
            description = "How the accepted answers split across each option, plus how many gave no "
                    + "answer at all — the authoritative counts behind the host's post-reveal \"who "
                    + "picked what\". Never names who; participants see only their own submission. "
                    + "Available only once the answer has been revealed.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The current distribution"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_HOST, or not the host",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown session",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "No question is in play "
                    + "(session.no-current-question) or not revealed yet "
                    + "(session.distribution.not-available)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public AnswerDistributionResponse answerDistribution(@PathVariable UUID id) {
        return AnswerDistributionResponse.from(
                answerDistributionQueryService.distribution(currentUserProvider.currentUser(), id));
    }

    @GetMapping("/{id}/leaderboard/top-five")
    @Operation(
            summary = "Read the Top 5 standings before and after this question (host only)",
            description = "The two authoritative boards the host's projected leaderboard animates "
                    + "between: the standings as they stood before the question in play, and as they "
                    + "stand now it has been revealed — five rows at most each, with each "
                    + "participant's previous and current rank, score, and the points this question "
                    + "awarded them. Host only: ranks 6 onward and other players' scores never reach "
                    + "a participant device before the podium. Available only once the answer is "
                    + "revealed, and never for the quiz's last question — that question has no "
                    + "interim leaderboard at all; its standings belong to the winner ceremony.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The Top 5 before-and-after"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_HOST, or not the host",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown session",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "No question is in play "
                    + "(session.no-current-question), or not revealed yet / the last question "
                    + "(session.top-five.not-available)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public TopFiveLeaderboardTransitionResponse topFiveLeaderboard(@PathVariable UUID id) {
        return TopFiveLeaderboardTransitionResponse.from(
                topFiveLeaderboardQueryService.transition(currentUserProvider.currentUser(), id));
    }

    @GetMapping("/{id}/final-standings")
    @Operation(
            summary = "Read a finished session's standings (host only)",
            description = "Every participant's finishing rank and score, with the name as it read at "
                    + "completion — captured when the session ended rather than recomputed, so a later "
                    + "change to the scoring or ranking rule cannot rewrite the result of an event that "
                    + "already happened. Host only: this is the complete field, and a participant reads "
                    + "only their own finish through the final-placement endpoint, which applies the "
                    + "reveal-group policy this one deliberately does not. Empty for a session that has "
                    + "not finished, or one that finished before this history was recorded.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The captured standings, in finishing order"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_HOST, or not the host",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown session",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public FinalStandingsResponse finalStandings(@PathVariable UUID id) {
        return FinalStandingsResponse.from(id,
                finalStandingsQueryService.standings(currentUserProvider.currentUser(), id));
    }

    @GetMapping("/{id}/participants/{participantId}/final-placement")
    @Operation(
            summary = "Read one participant's own finish",
            description = "The only participant-facing source of final ranking there is. Open like the "
                    + "personal-result endpoint (the unguessable session and participant ids gate it), "
                    + "and held until the host releases results — the winner ceremony runs first, "
                    + "always. Two shapes, decided by the server: the reveal group (the podium plus "
                    + "the top half) gets EXACT_RANK with their position and label; everyone else gets "
                    + "RELATIVE_ONLY — their score and the names either side of them, with no rank of "
                    + "their own, no neighbour rank, no neighbour score, and no score gap anywhere in "
                    + "the response.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The participant's own finish"),
            @ApiResponse(responseCode = "404", description = "Unknown session, or no such participant "
                    + "in it (session.participant.not-found)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "The quiz has not finished, or the host "
                    + "has not released results yet (session.results.not-available)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ParticipantFinalPlacementResponse finalPlacement(@PathVariable UUID id,
                                                            @PathVariable UUID participantId) {
        return ParticipantFinalPlacementResponse.from(
                participantFinalPlacementQueryService.placement(id, participantId));
    }

    @PostMapping("/{id}/results/release")
    @Operation(
            summary = "Release final standings to participants",
            description = "Host only. Lifts the final-results hold so every participant may read their "
                    + "own final rank — the one authoritative action following the host's winner "
                    + "ceremony. Idempotent: releasing an already-released session is harmless.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The session, with finalResultsReleased=true"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_HOST, or not the host",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown session",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "The session has not finished yet "
                    + "(session.invalid-transition)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public SessionSummaryResponse releaseResults(@PathVariable UUID id) {
        return SessionSummaryResponse.from(releaseFinalResultsApplicationService.release(
                currentUserProvider.currentUser(), id));
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

    @PostMapping("/{id}/questions/shuffle")
    @Operation(
            summary = "Shuffle this session's question order (host only)",
            description = "Randomises the order this session will play its questions in, without "
                    + "touching the quiz. A published quiz is immutable and a session pins the version "
                    + "it executes, so replaying one otherwise repeats an order the room already "
                    + "remembers; the shuffled order belongs to the session, so past sessions keep the "
                    + "questions they actually asked. The server draws the order — a caller-supplied "
                    + "permutation would be a client deciding gameplay, and would let a host preview "
                    + "the draw before accepting it. Allowed only before the first question opens.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The session, now with a shuffled order"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_HOST, or not the host",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown session",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "A question has already been played "
                    + "(session.invalid-transition)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public SessionSummaryResponse shuffleQuestions(@PathVariable UUID id) {
        return SessionSummaryResponse.from(shuffleQuestionsApplicationService.shuffle(
                currentUserProvider.currentUser(), id));
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
