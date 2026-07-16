package io.quizchef.session.api;

import io.quizchef.common.api.ApiError;
import io.quizchef.identity.domain.CurrentUserProvider;
import io.quizchef.session.application.CreateSessionApplicationService;
import io.quizchef.session.application.JoinSessionApplicationService;
import io.quizchef.session.application.OpenLobbyApplicationService;
import io.quizchef.session.application.ReconnectParticipantApplicationService;
import io.quizchef.session.application.SessionQueryService;
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
 * Session orchestration endpoints: create, open lobby, join, reconnect,
 * start, and read. Validate, delegate, respond — authorization and host
 * ownership are decided in the application services, never here.
 */
@RestController
@RequestMapping("/api/v1/sessions")
@Tag(name = "Sessions", description = "Hosting and joining live sessions: create, lobby, join, reconnect, start")
public class SessionController {

    private final CreateSessionApplicationService createSessionApplicationService;
    private final OpenLobbyApplicationService openLobbyApplicationService;
    private final JoinSessionApplicationService joinSessionApplicationService;
    private final ReconnectParticipantApplicationService reconnectParticipantApplicationService;
    private final StartSessionApplicationService startSessionApplicationService;
    private final SessionQueryService sessionQueryService;
    private final CurrentUserProvider currentUserProvider;

    public SessionController(CreateSessionApplicationService createSessionApplicationService,
                             OpenLobbyApplicationService openLobbyApplicationService,
                             JoinSessionApplicationService joinSessionApplicationService,
                             ReconnectParticipantApplicationService reconnectParticipantApplicationService,
                             StartSessionApplicationService startSessionApplicationService,
                             SessionQueryService sessionQueryService,
                             CurrentUserProvider currentUserProvider) {
        this.createSessionApplicationService = createSessionApplicationService;
        this.openLobbyApplicationService = openLobbyApplicationService;
        this.joinSessionApplicationService = joinSessionApplicationService;
        this.reconnectParticipantApplicationService = reconnectParticipantApplicationService;
        this.startSessionApplicationService = startSessionApplicationService;
        this.sessionQueryService = sessionQueryService;
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

    @PostMapping("/reconnect")
    @Operation(
            summary = "Reconnect to a session",
            description = "Rebinds to an existing participant, preserving identity, score, and answers. "
                    + "A guest sends their reconnection token; a registered player sends the session id "
                    + "and reconnects through their bearer token. Returns the reconnection snapshot.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reconnected; returns the session snapshot"),
            @ApiResponse(responseCode = "401", description = "A registered reconnect without a token",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "No participant matches (session.participant.not-found)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "The participant is not in a reconnectable state "
                    + "(participant.invalid-transition)",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public SessionSnapshotResponse reconnect(@Valid @RequestBody ReconnectRequest request) {
        return SessionSnapshotResponse.from(reconnectParticipantApplicationService.reconnect(
                currentUserProvider.currentUser(), request.toCommand()));
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
}
