package io.quizchef.session.api;

import io.quizchef.common.api.ApiError;
import io.quizchef.identity.domain.CurrentUserProvider;
import io.quizchef.session.application.CreateSessionApplicationService;
import io.quizchef.session.application.JoinSessionApplicationService;
import io.quizchef.session.application.OpenLobbyApplicationService;
import io.quizchef.session.application.GenerateRecoveryCodeApplicationService;
import io.quizchef.session.application.RedeemRecoveryCodeApplicationService;
import io.quizchef.session.application.ResumeParticipantApplicationService;
import io.quizchef.session.application.SessionQueryService;
import io.quizchef.session.application.SessionRosterQueryService;
import io.quizchef.session.application.SessionSummaryView;
import io.quizchef.session.application.StartSessionApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session orchestration endpoints: create, open lobby, join, resume,
 * start, and read. Validate, delegate, respond — authorization and host
 * ownership are decided in the application services, never here.
 */
@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions", description = "Hosting and joining live sessions: create, lobby, join, resume, start")
public class SessionController {

    private final CreateSessionApplicationService createSessionApplicationService;
    private final OpenLobbyApplicationService openLobbyApplicationService;
    private final JoinSessionApplicationService joinSessionApplicationService;
    private final ResumeParticipantApplicationService resumeParticipantApplicationService;
    private final GenerateRecoveryCodeApplicationService generateRecoveryCodeApplicationService;
    private final RedeemRecoveryCodeApplicationService redeemRecoveryCodeApplicationService;
    private final StartSessionApplicationService startSessionApplicationService;
    private final SessionQueryService sessionQueryService;
    private final SessionRosterQueryService sessionRosterQueryService;
    private final CurrentUserProvider currentUserProvider;

    public SessionController(CreateSessionApplicationService createSessionApplicationService,
                             OpenLobbyApplicationService openLobbyApplicationService,
                             JoinSessionApplicationService joinSessionApplicationService,
                             ResumeParticipantApplicationService resumeParticipantApplicationService,
                             GenerateRecoveryCodeApplicationService generateRecoveryCodeApplicationService,
                             RedeemRecoveryCodeApplicationService redeemRecoveryCodeApplicationService,
                             StartSessionApplicationService startSessionApplicationService,
                             SessionQueryService sessionQueryService,
                             SessionRosterQueryService sessionRosterQueryService,
                             CurrentUserProvider currentUserProvider) {
        this.createSessionApplicationService = createSessionApplicationService;
        this.openLobbyApplicationService = openLobbyApplicationService;
        this.joinSessionApplicationService = joinSessionApplicationService;
        this.resumeParticipantApplicationService = resumeParticipantApplicationService;
        this.generateRecoveryCodeApplicationService = generateRecoveryCodeApplicationService;
        this.redeemRecoveryCodeApplicationService = redeemRecoveryCodeApplicationService;
        this.startSessionApplicationService = startSessionApplicationService;
        this.sessionQueryService = sessionQueryService;
        this.sessionRosterQueryService = sessionRosterQueryService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    @Operation(
            summary = "Create a session",
            description = "Creates a session for a published quiz version, hosted by the caller, and "
                    + "assigns a unique PIN. Requires QUIZ_HOST.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Session created; Location points to it"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated but lacking QUIZ_HOST",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "The quiz version does not exist",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "The quiz is not published (quiz.not-published)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<SessionSummaryResponse> create(
            @Valid @RequestBody CreateSessionRequest request) {
        SessionSummaryView view = createSessionApplicationService.create(
                currentUserProvider.currentUser(), request.toCommand());
        return ResponseEntity
                .created(URI.create("/api/v1/sessions/" + view.sessionId()))
                .body(SessionSummaryResponse.from(view));
    }

    @PostMapping("/{pin}/lobby")
    @Operation(
            summary = "Open the lobby",
            description = "Moves the session from CREATED to LOBBY so participants may join. Host only, "
                    + "requires QUIZ_HOST.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The session, now in LOBBY"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_HOST, or not the host",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No active session for the PIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "The session is not in CREATED (session.invalid-transition)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public SessionSummaryResponse openLobby(@PathVariable String pin) {
        return SessionSummaryResponse.from(openLobbyApplicationService.openLobby(
                currentUserProvider.currentUser(), pin));
    }

    @PostMapping("/{pin}/join")
    @Operation(
            summary = "Join a session by PIN",
            description = "Open to everyone — anonymous callers join as guests (and receive a "
                    + "reconnection token), authenticated callers join backed by their identity. The "
                    + "session must be accepting joins (lobby, or in progress if late join is enabled) "
                    + "and not full.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Joined; a guest's token is in the response"),
            @ApiResponse(responseCode = "400", description = "Validation failed (blank name, bad language tag)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No active session for the PIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Lobby closed (session.invalid-transition), "
                    + "full (session.full), or already joined (session.participant.already-joined)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ParticipantSessionResponse join(@PathVariable String pin,
                                           @Valid @RequestBody JoinSessionRequest request) {
        return ParticipantSessionResponse.from(joinSessionApplicationService.join(
                currentUserProvider.currentUser(), request.toCommand(pin)));
    }

    @PostMapping("/{pin}/participants/resume")
    @Operation(
            summary = "Resume an existing participant",
            description = "Returns a player to the participant they already are in this session, with "
                    + "their score, answers, name, and language intact, and returns the resume "
                    + "snapshot. A guest presents the resume token issued at join; a registered player "
                    + "sends an empty body and is resolved from their bearer identity.\n\n"
                    + "Called on every arrival — refresh, reopened tab, dropped connection, or a return "
                    + "after several questions — and always before join, which is what stops a "
                    + "returning player being offered the join form and becoming a second participant "
                    + "with none of their score.\n\n"
                    + "Addressed by PIN, and resolved to the session live under that PIN right now. "
                    + "PINs are reused once a session is archived, so a stored credential may belong to "
                    + "a quiz that has already finished: resolving from the PIN means such a player is "
                    + "told they are not in this session and can join it, rather than being silently "
                    + "restored into the old one. A token issued for a different live session does not "
                    + "resolve here either, so it cannot be replayed across quizzes.\n\n"
                    + "Identity comes from the token, never from a display name, and never from a "
                    + "participant id — that identifies, it does not authenticate.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumed; returns the session snapshot"),
            @ApiResponse(responseCode = "401", description = "No resume token and not signed in",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No active session for the PIN, or no "
                    + "participant in it matches (session.participant.not-found) — an unknown, "
                    + "expired, or other session's token is indistinguishable here on purpose",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "The participant has finished and cannot "
                    + "resume (participant.invalid-transition)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public SessionSnapshotResponse resume(@PathVariable String pin,
                                          @Valid @RequestBody(required = false)
                                          ResumeParticipantRequest request) {
        ResumeParticipantRequest resolved =
                request == null ? new ResumeParticipantRequest(null) : request;
        return SessionSnapshotResponse.from(resumeParticipantApplicationService.resume(
                currentUserProvider.currentUser(), resolved.toCommand(pin)));
    }

    @PostMapping("/{pin}/participants/recover")
    @Operation(
            summary = "Recover a participant with a host-issued code",
            description = "The last resort for a player whose browser lost its resume "
                    + "credential. They can prove nothing by themselves — that is what the resume "
                    + "token is for — and letting them back in on their display name would hand "
                    + "their score to anyone who heard it. The missing authority comes from the "
                    + "host, who can see the person asking.\n\n"
                    + "The code is spent doing this: single-use, a few minutes old at most, bound "
                    + "to one participant in one session, and rate limited. On success the "
                    + "participant's resume token is **rotated** — the device that lost the game "
                    + "stops being able to resume — and the new one is returned once, here. "
                    + "Everything else is untouched: same id, name, language, answers, and score.\n\n"
                    + "Every refusal looks the same (unknown, expired, already used, wrong "
                    + "session, malformed), so the response cannot be used to map which codes "
                    + "exist.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recovered; a new resume token and the "
                    + "session snapshot"),
            @ApiResponse(responseCode = "400", description = "Validation failed (missing code)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No active session for the PIN",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "The code was not accepted "
                    + "(participant.recovery.code-not-accepted)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Too many attempts")
    })
    public RecoveredParticipantResponse recoverParticipant(
            @PathVariable String pin,
            @Valid @RequestBody RedeemRecoveryCodeRequest request) {
        return RecoveredParticipantResponse.from(
                redeemRecoveryCodeApplicationService.redeem(pin, request.recoveryCode()));
    }

    @PostMapping("/{id}/participants/{participantId}/recovery-code")
    @Operation(
            summary = "Issue a recovery code for one participant (host only)",
            description = "Mints six digits the host reads out to a player who cannot get back in. "
                    + "Returned exactly once and never stored in the clear. Issuing supersedes any "
                    + "code already outstanding for that participant, so a host who clicks twice or "
                    + "misreads the first one does not leave two live codes for the same player.\n\n"
                    + "Guests only: a signed-in player rejoins by logging in from any device, and a "
                    + "code would be a second, weaker way in.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The code, its expiry, and whose it is"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_HOST, or not the host",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown session, or no such participant "
                    + "in it (session.participant.not-found)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "The player is signed in, or the quiz "
                    + "has finished (participant.recovery.not-available)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public RecoveryCodeResponse issueRecoveryCode(@PathVariable UUID id,
                                                  @PathVariable UUID participantId) {
        return RecoveryCodeResponse.from(generateRecoveryCodeApplicationService.generate(
                currentUserProvider.currentUser(), id, participantId));
    }

    @PostMapping("/{id}/start")
    @Operation(
            summary = "Start a session",
            description = "Moves the session from LOBBY to IN_PROGRESS. Host only, requires QUIZ_HOST. "
                    + "Needs at least one participant. No question opens and no timer starts here — that "
                    + "is the gameplay engine.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The session, now IN_PROGRESS"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_HOST, or not the host",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown session",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Not in LOBBY (session.invalid-transition) or "
                    + "empty (session.not-startable)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public SessionSummaryResponse start(@PathVariable UUID id) {
        return SessionSummaryResponse.from(startSessionApplicationService.start(
                currentUserProvider.currentUser(), id));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Read a session summary",
            description = "Metadata, host, lifecycle state, roster size, and settings — no gameplay "
                    + "state. Open by id so lobby participants (guests included) can see it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The session summary"),
            @ApiResponse(responseCode = "404", description = "Unknown session",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public SessionSummaryResponse get(@PathVariable UUID id) {
        return SessionSummaryResponse.from(sessionQueryService.summary(id));
    }

    @GetMapping("/{id}/participants")
    @Operation(
            summary = "Read the roster (host only)",
            description = "Every joined participant's display name and connection state, in stable "
                    + "join order — what the projected lobby wall renders. Host only: names across "
                    + "the whole roster are the host's projection; realtime join events deliberately "
                    + "carry only ids, so the wall re-reads this on each roster event. Closes "
                    + "RFC-004's flagged \"no roster read endpoint yet\" gap.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The roster, in join order"),
            @ApiResponse(responseCode = "401", description = "Missing, invalid, or revoked token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Lacking QUIZ_HOST, or not the host",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Unknown session",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public SessionParticipantsResponse participants(@PathVariable UUID id) {
        return SessionParticipantsResponse.from(
                id, sessionRosterQueryService.roster(currentUserProvider.currentUser(), id));
    }
}
