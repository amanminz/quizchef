import { apiClient } from "@/api/axios";
import type {
  AnswerAcceptedResponse,
  AnswerDistributionResponse,
  AnswerProgressResponse,
  CorrectQuestionRequest,
  CreateSessionRequest,
  CurrentQuestionResponse,
  JoinSessionRequest,
  LeaderboardResponse,
  FinalStandingsResponse,
  ParticipantFinalPlacementResponse,
  ParticipantResultResponse,
  ParticipantSessionResponse,
  ResumeParticipantRequest,
  SessionParticipantsResponse,
  SessionQuestionsResponse,
  SessionResultsResponse,
  SessionSnapshotResponse,
  SessionSummaryResponse,
  SubmitAnswerRequest,
  TopFiveLeaderboardTransitionResponse
} from "@/types/api";

/**
 * Session lifecycle and gameplay endpoints (RFC-004). Host commands require
 * a bearer token; join/resume/answer are anonymous-friendly — the token
 * interceptor simply has nothing to inject for a guest.
 */
export const sessionApi = {
  async create(request: CreateSessionRequest): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.post<SessionSummaryResponse>("/api/v1/sessions", request);
    return data;
  },

  async getById(sessionId: string): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.get<SessionSummaryResponse>(`/api/v1/sessions/${sessionId}`);
    return data;
  },

  async openLobby(sessionPin: string): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.post<SessionSummaryResponse>(
      `/api/v1/sessions/${sessionPin}/lobby`
    );
    return data;
  },

  async join(sessionPin: string, request: JoinSessionRequest): Promise<ParticipantSessionResponse> {
    const { data } = await apiClient.post<ParticipantSessionResponse>(
      `/api/v1/sessions/${sessionPin}/join`,
      request
    );
    return data;
  },

  /**
   * Returns a player to the participant they already are in the session
   * live under this PIN — same score, same answers, same name and
   * language. Called on every arrival (refresh, reopened tab, dropped
   * connection, long absence) and always BEFORE join: a returning player
   * who is offered the join form instead becomes a second participant with
   * none of their score.
   *
   * A guest sends the resume token issued once at join; a signed-in player
   * sends nothing and is resolved from their bearer token. Throws
   * `session.participant.not-found` (404) when the credential does not
   * belong to the session currently live under this PIN — which is also
   * what a stale credential from an earlier quiz that reused the code
   * looks like, and is the signal to forget it and show the join form.
   */
  async resumeParticipant(
    pin: string,
    request: ResumeParticipantRequest
  ): Promise<SessionSnapshotResponse> {
    const { data } = await apiClient.post<SessionSnapshotResponse>(
      `/api/v1/sessions/${pin}/participants/resume`,
      request
    );
    return data;
  },

  async start(sessionId: string): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.post<SessionSummaryResponse>(
      `/api/v1/sessions/${sessionId}/start`
    );
    return data;
  },

  async startQuestion(sessionId: string): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.post<SessionSummaryResponse>(
      `/api/v1/sessions/${sessionId}/questions/start`
    );
    return data;
  },

  async closeQuestion(sessionId: string): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.post<SessionSummaryResponse>(
      `/api/v1/sessions/${sessionId}/questions/close`
    );
    return data;
  },

  async revealAnswer(sessionId: string): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.post<SessionSummaryResponse>(
      `/api/v1/sessions/${sessionId}/questions/reveal`
    );
    return data;
  },

  async showLeaderboard(sessionId: string): Promise<LeaderboardResponse> {
    const { data } = await apiClient.post<LeaderboardResponse>(
      `/api/v1/sessions/${sessionId}/leaderboard`
    );
    return data;
  },

  async advanceQuestion(sessionId: string): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.post<SessionSummaryResponse>(
      `/api/v1/sessions/${sessionId}/questions/advance`
    );
    return data;
  },

  async submitAnswer(
    sessionId: string,
    request: SubmitAnswerRequest
  ): Promise<AnswerAcceptedResponse> {
    const { data } = await apiClient.post<AnswerAcceptedResponse>(
      `/api/v1/sessions/${sessionId}/answers`,
      request
    );
    return data;
  },

  /**
   * The question in play, participant-safe (no correctness or explanation
   * until revealed). Public — anonymous guests read it too. Throws
   * `session.no-current-question` (409) between questions.
   */
  async currentQuestion(sessionId: string): Promise<CurrentQuestionResponse> {
    const { data } = await apiClient.get<CurrentQuestionResponse>(
      `/api/v1/sessions/${sessionId}/questions/current`
    );
    return data;
  },

  /**
   * The current question's answer progress — HOST ONLY: how many
   * participants have an accepted answer out of how many could answer
   * right now. Counts only, never who. Refreshed on each answer.progress
   * broadcast; throws `session.no-current-question` (409) between
   * questions.
   */
  async answerProgress(sessionId: string): Promise<AnswerProgressResponse> {
    const { data } = await apiClient.get<AnswerProgressResponse>(
      `/api/v1/sessions/${sessionId}/answer-progress`
    );
    return data;
  },

  /**
   * Randomises the order this session plays its questions in — host only,
   * and only before the first question opens. The quiz is never modified:
   * published content is immutable and past sessions keep the questions
   * they actually asked. The server draws the order, so there is nothing
   * to send and nothing to preview.
   */
  async shuffleQuestions(sessionId: string): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.post<SessionSummaryResponse>(
      `/api/v1/sessions/${sessionId}/questions/shuffle`
    );
    return data;
  },

  /**
   * This session's questions — HOST ONLY. The sequence being played, in the
   * session's own order, with each question's number, status, and whether
   * it has already been corrected; removed questions are listed where they
   * used to sit, without a number.
   *
   * Unlike every other question read, this one carries `correctOptionIds`
   * before the reveal — a host cannot fix a wrong answer key without being
   * shown it. Render it only inside the correction dialog, never in a row
   * that could reach a projector.
   */
  async sessionQuestions(sessionId: string): Promise<SessionQuestionsResponse> {
    const { data } = await apiClient.get<SessionQuestionsResponse>(
      `/api/v1/sessions/${sessionId}/questions`
    );
    return data;
  },

  /**
   * Corrects one question for this session only — HOST ONLY. The Question
   * Library record is never touched. Whether this also replays the question
   * is the server's decision, not this call's: an upcoming question is
   * corrected silently, while the question in play is restarted from its
   * reading period with every answer and point it produced reversed.
   * Throws `session.invalid-transition` (409) for a question already
   * played, already removed, or in a finished session.
   */
  async correctQuestion(
    sessionId: string,
    questionId: string,
    request: CorrectQuestionRequest
  ): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.post<SessionSummaryResponse>(
      `/api/v1/sessions/${sessionId}/questions/${questionId}/correction`,
      request
    );
    return data;
  },

  /**
   * Pulls a question out of this session — HOST ONLY, and idempotent, so a
   * double-click converges rather than conflicting. Answers cancelled,
   * points reversed, numbering closed up. Removing the question in play
   * advances to the next one, or finishes the session when there is none.
   * Throws `session.question.removal-not-allowed` (409) for the last
   * question left to play.
   */
  async removeQuestion(sessionId: string, questionId: string): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.post<SessionSummaryResponse>(
      `/api/v1/sessions/${sessionId}/questions/${questionId}/removal`
    );
    return data;
  },

  /**
   * The full standings — HOST ONLY since the live-event privacy split:
   * every name, score, and rank is the host's projection. Interim (once
   * revealed / on the leaderboard) and final (after FINISHED) share this
   * one shape; throws `session.results.not-available` (409) while a
   * question is still being played. Distinct from `showLeaderboard`, the
   * host's phase-transitioning command — this never changes anything.
   */
  async results(sessionId: string): Promise<SessionResultsResponse> {
    const { data } = await apiClient.get<SessionResultsResponse>(
      `/api/v1/sessions/${sessionId}/results`
    );
    return data;
  },

  /**
   * One participant's own row — rank, score, framing counts, and nothing
   * about anyone else. Anonymous-friendly: the unguessable session and
   * participant ids gate it, the same trust `submitAnswer` places in the
   * participant id. Phase-gated exactly like `results`.
   */
  async participantResult(
    sessionId: string,
    participantId: string
  ): Promise<ParticipantResultResponse> {
    const { data } = await apiClient.get<ParticipantResultResponse>(
      `/api/v1/sessions/${sessionId}/participants/${participantId}/result`
    );
    return data;
  },

  /**
   * The roster, host only: every joined participant's display name and
   * connection state in stable join order — what the projected lobby wall
   * renders. Join events carry only ids, so the wall re-reads this on each
   * roster event.
   */
  async participants(sessionId: string): Promise<SessionParticipantsResponse> {
    const { data } = await apiClient.get<SessionParticipantsResponse>(
      `/api/v1/sessions/${sessionId}/participants`
    );
    return data;
  },

  /**
   * How the current question's answers split across each option — HOST
   * ONLY, counts and percentages never names. Available only once the
   * answer is revealed; throws `session.distribution.not-available` (409)
   * before that.
   */
  async answerDistribution(sessionId: string): Promise<AnswerDistributionResponse> {
    const { data } = await apiClient.get<AnswerDistributionResponse>(
      `/api/v1/sessions/${sessionId}/answer-distribution`
    );
    return data;
  },

  /**
   * The two authoritative boards the host's projected Top 5 animates
   * between — the standings before this question, and after it — HOST
   * ONLY: five rows at most, and ranks 6 onward never reach a participant
   * device before the podium. Throws `session.top-five.not-available`
   * (409) before the reveal and, always, for the quiz's last question:
   * that question has no interim leaderboard at all.
   */
  async topFiveTransition(sessionId: string): Promise<TopFiveLeaderboardTransitionResponse> {
    const { data } = await apiClient.get<TopFiveLeaderboardTransitionResponse>(
      `/api/v1/sessions/${sessionId}/leaderboard/top-five`
    );
    return data;
  },

  /**
   * One participant's own finish — the only participant-facing source of
   * final ranking there is. Anonymous-friendly like `participantResult`,
   * and held until the host releases results (409
   * `session.results.not-available` before that). Read `visibility`
   * first: `EXACT_RANK` carries their position and label; `RELATIVE_ONLY`
   * carries their score and the names either side of them, with no rank
   * of their own and no neighbour rank, score, or gap anywhere in it.
   */
  async finalPlacement(
    sessionId: string,
    participantId: string
  ): Promise<ParticipantFinalPlacementResponse> {
    const { data } = await apiClient.get<ParticipantFinalPlacementResponse>(
      `/api/v1/sessions/${sessionId}/participants/${participantId}/final-placement`
    );
    return data;
  },

  /**
   * A finished session's standings as captured when it ended — HOST ONLY,
   * and the complete field. Read back from history rather than recomputed,
   * so a later change to scoring or ranking cannot rewrite a past event.
   * Empty for a session that has not finished, or one that finished before
   * this history existed; neither is an error.
   */
  async finalStandings(sessionId: string): Promise<FinalStandingsResponse> {
    const { data } = await apiClient.get<FinalStandingsResponse>(
      `/api/v1/sessions/${sessionId}/final-standings`
    );
    return data;
  },

  /**
   * Host-only, idempotent: lifts the final-results hold so every
   * participant may read their own final rank through `participantResult`.
   * Throws `session.invalid-transition` (409) if the session has not
   * finished yet.
   */
  async releaseFinalResults(sessionId: string): Promise<SessionSummaryResponse> {
    const { data } = await apiClient.post<SessionSummaryResponse>(
      `/api/v1/sessions/${sessionId}/results/release`
    );
    return data;
  }
};
