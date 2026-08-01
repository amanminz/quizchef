import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { usePlayerSessionStore } from "@/features/gameplay/playerSessionStore";
import { sessionTopic } from "@/realtime/SessionSubscriptions";
import { fakeRealtimeClient, protocolMessage } from "@/test/fakeStomp";
import {
  currentQuestionResponse,
  participantRankContextResponse,
  participantResultResponse,
  participantSessionResponse,
  previewQuestionResponse,
  revealedQuestionResponse,
  sessionSnapshotResponse
} from "@/test/gameplayFixtures";
import { apiError } from "@/test/handlers";
import { sessionSummary } from "@/test/sessionFixtures";
import { server } from "@/test/server";
import { currentPath, renderApp } from "@/test/testUtils";
import type { CurrentQuestionResponse, SessionSummaryResponse } from "@/types/api";

const PIN = "042317";

/** Wires the session/question/answer endpoints a joined participant hits. */
function serveGameplay(session: SessionSummaryResponse, question: CurrentQuestionResponse) {
  server.use(
    http.get(`/api/v1/sessions/${session.sessionId}`, () => HttpResponse.json(session)),
    http.get(`/api/v1/sessions/${session.sessionId}/questions/current`, () =>
      HttpResponse.json(question)
    )
  );
}

describe("PlaySessionPage", () => {
  it("joins from the PIN entry and lands on the question", async () => {
    const question = currentQuestionResponse();
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "QUESTION_OPEN"
    });
    const participant = participantSessionResponse({
      sessionId: session.sessionId,
      sessionState: "IN_PROGRESS"
    });
    serveGameplay(session, question);
    server.use(
      http.post(`/api/v1/sessions/${PIN}/join`, () => HttpResponse.json(participant)),
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: participant.participantId,
            currentQuestionId: question.questionId,
            currentPhase: "QUESTION_OPEN",
            submittedOptionIds: []
          })
        )
      )
    );
    const user = userEvent.setup();

    renderApp("/play");
    await user.type(screen.getByLabelText(/session code/i), PIN);
    await user.type(screen.getByLabelText(/your name/i), "Aman");
    await user.click(screen.getByRole("button", { name: /^join$/i }));

    await waitFor(() => expect(currentPath()).toBe(`/play/${PIN}`));
    expect(await screen.findByText(question.localizations![0].prompt!)).toBeInTheDocument();
    expect(screen.getByText("True")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /submit answer/i })).toBeInTheDocument();
  });

  it("submits an answer, shows confirmation, and cannot submit again", async () => {
    const question = currentQuestionResponse();
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "QUESTION_OPEN"
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-1",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    serveGameplay(session, question);
    let submitCount = 0;
    server.use(
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            currentQuestionId: question.questionId,
            currentPhase: "QUESTION_OPEN",
            submittedOptionIds: []
          })
        )
      ),
      http.post(`/api/v1/sessions/${session.sessionId}/answers`, () => {
        submitCount += 1;
        return HttpResponse.json({
          participantId: record.participantId,
          questionId: question.questionId,
          accepted: true
        });
      })
    );
    const user = userEvent.setup();

    renderApp(`/play/${PIN}`);
    await user.click(await screen.findByRole("button", { name: "True" }));
    await user.click(screen.getByRole("button", { name: /submit answer/i }));

    expect(await screen.findByText(/answer submitted/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /submit answer/i })).not.toBeInTheDocument();
    expect(submitCount).toBe(1);
  });

  it("recovers an already-submitted answer after a refresh", async () => {
    const question = currentQuestionResponse();
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "QUESTION_OPEN"
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-1",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    serveGameplay(session, question);
    server.use(
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            currentQuestionId: question.questionId,
            currentPhase: "QUESTION_OPEN",
            submittedOptionIds: [question.options![0].optionId!]
          })
        )
      )
    );

    renderApp(`/play/${PIN}`);

    expect(await screen.findByText(/answer submitted/i)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "True" })).not.toBeInTheDocument();
  });

  it("disables answering once the question's time has run out", async () => {
    const question = currentQuestionResponse({
      endsAt: new Date(Date.now() - 5_000).toISOString()
    });
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "QUESTION_OPEN"
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-1",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    serveGameplay(session, question);
    server.use(
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            currentQuestionId: question.questionId,
            currentPhase: "QUESTION_OPEN",
            submittedOptionIds: []
          })
        )
      )
    );

    renderApp(`/play/${PIN}`);

    expect(await screen.findByRole("button", { name: "True" })).toBeDisabled();
    expect(screen.getByRole("button", { name: /submit answer/i })).toBeDisabled();
  });

  it("shows the prompt with no options during the reading period, and cannot submit", async () => {
    const base = currentQuestionResponse();
    const previewing = previewQuestionResponse(base);
    const session = sessionSummary({
      sessionId: previewing.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: previewing.questionId,
      currentPhase: "QUESTION_PREVIEW"
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-1",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    serveGameplay(session, previewing);
    server.use(
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            currentQuestionId: previewing.questionId,
            currentPhase: "QUESTION_PREVIEW",
            submittedOptionIds: []
          })
        )
      )
    );

    renderApp(`/play/${PIN}`);

    expect(await screen.findByText(base.localizations![0].prompt!)).toBeInTheDocument();
    expect(screen.getByText(/get ready/i)).toBeInTheDocument();
    // The response genuinely carries no option data — there is nothing to
    // render or to disable.
    expect(screen.queryByText("True")).not.toBeInTheDocument();
    expect(screen.queryByText("False")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /submit answer/i })).not.toBeInTheDocument();
  });

  it("shows options only once the authoritative phase transitions to open", async () => {
    const openQuestion = currentQuestionResponse({ phase: "QUESTION_OPEN" });
    const holder = {
      session: sessionSummary({
        sessionId: openQuestion.sessionId,
        state: "IN_PROGRESS",
        currentQuestionId: openQuestion.questionId,
        currentPhase: "QUESTION_PREVIEW" as const
      }),
      question: previewQuestionResponse(openQuestion)
    };
    const record = {
      sessionId: holder.session.sessionId!,
      participantId: "participant-1",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    server.use(
      http.get(`/api/v1/sessions/${holder.session.sessionId}`, () =>
        HttpResponse.json(holder.session)
      ),
      http.get(`/api/v1/sessions/${holder.session.sessionId}/questions/current`, () =>
        HttpResponse.json(holder.question)
      ),
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: holder.session.sessionId,
            participantId: record.participantId,
            currentQuestionId: openQuestion.questionId,
            currentPhase: "QUESTION_PREVIEW",
            submittedOptionIds: []
          })
        )
      )
    );
    const { client, fake } = fakeRealtimeClient();

    renderApp(`/play/${PIN}`, { realtimeClient: client });
    await screen.findByText(/get ready/i);
    act(() => fake.simulateConnect());

    holder.session = { ...holder.session, currentPhase: "QUESTION_OPEN" };
    holder.question = openQuestion;
    act(() => {
      fake.deliver(
        sessionTopic(holder.session.sessionId!),
        protocolMessage("question.started", holder.session.sessionId!, {
          questionId: openQuestion.questionId,
          endsAt: openQuestion.endsAt,
          durationSeconds: openQuestion.durationSeconds
        })
      );
    });

    expect(await screen.findByRole("button", { name: "True" })).toBeInTheDocument();
    expect(screen.queryByText(/get ready/i)).not.toBeInTheDocument();
  });

  it("reconnecting straight into an open question never replays the reading period", async () => {
    const openQuestion = currentQuestionResponse({ phase: "QUESTION_OPEN" });
    const session = sessionSummary({
      sessionId: openQuestion.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: openQuestion.questionId,
      currentPhase: "QUESTION_OPEN"
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-1",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    serveGameplay(session, openQuestion);
    server.use(
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            currentQuestionId: openQuestion.questionId,
            currentPhase: "QUESTION_OPEN",
            submittedOptionIds: []
          })
        )
      )
    );

    renderApp(`/play/${PIN}`);

    expect(await screen.findByRole("button", { name: "True" })).toBeInTheDocument();
    expect(screen.queryByText(/get ready/i)).not.toBeInTheDocument();
  });

  it("shows the waiting overlay after the question closes remotely, then the next question", async () => {
    const question = currentQuestionResponse({ questionNumber: 1 });
    const holder = {
      session: sessionSummary({
        sessionId: question.sessionId,
        state: "IN_PROGRESS",
        currentQuestionId: question.questionId,
        currentPhase: "QUESTION_OPEN"
      }),
      question
    };
    const record = {
      sessionId: holder.session.sessionId!,
      participantId: "participant-1",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    server.use(
      http.get(`/api/v1/sessions/${holder.session.sessionId}`, () =>
        HttpResponse.json(holder.session)
      ),
      http.get(`/api/v1/sessions/${holder.session.sessionId}/questions/current`, () => {
        // The real endpoint returns 200 through every phase of a question in
        // play (open, closed, revealed) — it 409s only once there is no
        // current question at all (RFC-004's CurrentQuestionQueryService).
        if (!holder.session.currentQuestionId) {
          return HttpResponse.json(
            apiError("session.no-current-question", "No question is in play"),
            { status: 409 }
          );
        }
        return HttpResponse.json({ ...holder.question, phase: holder.session.currentPhase });
      }),
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: holder.session.sessionId,
            participantId: record.participantId,
            currentQuestionId: holder.question.questionId,
            currentPhase: "QUESTION_OPEN",
            submittedOptionIds: []
          })
        )
      )
    );
    const { client, fake } = fakeRealtimeClient();

    renderApp(`/play/${PIN}`, { realtimeClient: client });
    await screen.findByText(holder.question.localizations![0].prompt!);
    act(() => fake.simulateConnect());

    holder.session = { ...holder.session, currentPhase: "QUESTION_CLOSED" };
    act(() => {
      fake.deliver(
        sessionTopic(holder.session.sessionId!),
        protocolMessage("question.closed", holder.session.sessionId!, {
          questionId: holder.question.questionId
        })
      );
    });

    expect(await screen.findByText(/waiting for next question/i)).toBeInTheDocument();

    const nextQuestion = currentQuestionResponse({
      sessionId: holder.session.sessionId,
      questionId: "question-2",
      questionNumber: 2
    });
    holder.session = {
      ...holder.session,
      currentQuestionId: nextQuestion.questionId,
      currentPhase: "QUESTION_OPEN"
    };
    holder.question = nextQuestion;
    act(() => {
      fake.deliver(
        sessionTopic(holder.session.sessionId!),
        protocolMessage("question.started", holder.session.sessionId!, {
          questionId: nextQuestion.questionId,
          endsAt: nextQuestion.endsAt,
          durationSeconds: nextQuestion.durationSeconds
        })
      );
    });

    expect(await screen.findByText(/question 2 of/i)).toBeInTheDocument();
  });

  it("shows a connection-lost banner and clears it on reconnect", async () => {
    const question = currentQuestionResponse();
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "QUESTION_OPEN"
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-1",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    serveGameplay(session, question);
    server.use(
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            currentQuestionId: question.questionId,
            currentPhase: "QUESTION_OPEN",
            submittedOptionIds: []
          })
        )
      )
    );
    const { client, fake } = fakeRealtimeClient();

    renderApp(`/play/${PIN}`, { realtimeClient: client });
    await screen.findByText(question.localizations![0].prompt!);
    act(() => fake.simulateConnect());

    act(() => fake.simulateConnectionLost());
    expect(await screen.findByRole("alert")).toHaveTextContent(/connection lost/i);

    act(() => fake.simulateConnect());
    await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
  });

  it("returns to the join form when the server no longer recognizes the participant", async () => {
    usePlayerSessionStore.getState().record(PIN, {
      sessionId: "stale-session",
      participantId: "stale-participant",
      guestParticipantToken: "stale-token",
      displayName: "Aman",
      preferredLanguage: "en"
    });
    server.use(
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(apiError("session.participant.not-found", "No participant matches"), {
          status: 404
        })
      )
    );

    renderApp(`/play/${PIN}`);

    expect(await screen.findByLabelText(/your name/i)).toBeInTheDocument();
    expect(usePlayerSessionStore.getState().bySessionPin[PIN]).toBeUndefined();
  });

  it("shows the participant a correct verdict, their answer, and the explanation at the reveal", async () => {
    const base = currentQuestionResponse();
    const question = revealedQuestionResponse(base);
    const correctOptionId = base.options![0].optionId!;
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "ANSWER_REVEALED"
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-1",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    serveGameplay(session, question);
    server.use(
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            currentQuestionId: question.questionId,
            currentPhase: "ANSWER_REVEALED",
            // The participant answered correctly — restored by the snapshot.
            submittedOptionIds: [correctOptionId]
          })
        )
      )
    );

    renderApp(`/play/${PIN}`);

    expect(await screen.findByText("Correct!")).toBeInTheDocument();
    expect(screen.getByText("Correct answer")).toBeInTheDocument();
    expect(screen.getByText("Your answer")).toBeInTheDocument();
    expect(screen.getByText(/jonah 1:17 tells the story/i)).toBeInTheDocument();
  });

  it("shows an incorrect verdict when the submitted answer misses", async () => {
    const base = currentQuestionResponse();
    const question = revealedQuestionResponse(base);
    const wrongOptionId = base.options![1].optionId!;
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "ANSWER_REVEALED"
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-1",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    serveGameplay(session, question);
    server.use(
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            currentQuestionId: question.questionId,
            currentPhase: "ANSWER_REVEALED",
            submittedOptionIds: [wrongOptionId]
          })
        )
      )
    );

    renderApp(`/play/${PIN}`);

    expect(await screen.findByText("Not quite.")).toBeInTheDocument();
  });

  it("shows only the participant's own rank at the leaderboard step", async () => {
    const question = currentQuestionResponse({ phase: "LEADERBOARD" });
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "LEADERBOARD"
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-me",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    serveGameplay(session, question);
    server.use(
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            currentQuestionId: question.questionId,
            currentPhase: "LEADERBOARD"
          })
        )
      ),
      http.get(`/api/v1/sessions/${session.sessionId}/results`, () => {
        fullStandingsCalled = true;
        return HttpResponse.json(apiError("auth.unauthorized", "Host only"), { status: 401 });
      }),
      http.get(
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/result`,
        () =>
          HttpResponse.json(
            participantResultResponse({
              sessionId: session.sessionId,
              participantId: record.participantId,
              rank: 2,
              score: 320
            })
          )
      )
    );
    let fullStandingsCalled = false;

    renderApp(`/play/${PIN}`);

    // Only the personal result renders — never a table, never a rival name.
    expect(await screen.findByText("Your rank")).toBeInTheDocument();
    expect(screen.getByText("2nd")).toBeInTheDocument();
    expect(screen.getByText("320")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.queryByText("Ann")).not.toBeInTheDocument();
    // The participant device never even calls the host's standings API.
    expect(fullStandingsCalled).toBe(false);
  });

  it("shows the waiting-for-announcement screen at the final question's leaderboard step, before the session finishes", async () => {
    // The session is still IN_PROGRESS — the host hasn't clicked "Finish
    // Quiz" yet — but this is the quiz's last question, so the same hold
    // that applies once FINISHED-but-not-released must already apply here.
    const question = currentQuestionResponse({
      phase: "LEADERBOARD",
      questionNumber: 2,
      totalQuestions: 2
    });
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "LEADERBOARD"
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-me",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    serveGameplay(session, question);
    let personalResultCalled = false;
    let rankContextCalled = false;
    server.use(
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            currentQuestionId: question.questionId,
            currentPhase: "LEADERBOARD"
          })
        )
      ),
      http.get(
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/result`,
        () => {
          personalResultCalled = true;
          return HttpResponse.json(
            apiError("session.results.not-available", "Results are not available"),
            { status: 409 }
          );
        }
      ),
      http.get(
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/rank-context`,
        () => {
          rankContextCalled = true;
          return HttpResponse.json(
            apiError("session.rank-context.not-available", "Rank context is not available"),
            { status: 409 }
          );
        }
      )
    );

    const firstMount = renderApp(`/play/${PIN}`);

    expect(await screen.findByText(/quiz complete/i)).toBeInTheDocument();
    expect(screen.getByText(/winners are being announced/i)).toBeInTheDocument();
    // No rank, no neighbour, nothing from the previous leaderboard render.
    expect(screen.queryByText("Your rank")).not.toBeInTheDocument();
    expect(screen.queryByText("Ahead of you")).not.toBeInTheDocument();
    expect(screen.queryByText("Behind you")).not.toBeInTheDocument();
    // Neither gated read is even attempted while the hold applies.
    expect(personalResultCalled).toBe(false);
    expect(rankContextCalled).toBe(false);

    // A refresh/reconnect during this exact window still shows no rank.
    firstMount.unmount();
    renderApp(`/play/${PIN}`);
    expect(await screen.findByText(/quiz complete/i)).toBeInTheDocument();
    expect(personalResultCalled).toBe(false);
    expect(rankContextCalled).toBe(false);
  });

  it("shows one participant ahead and one behind after a non-final question", async () => {
    const question = currentQuestionResponse({ phase: "LEADERBOARD", questionNumber: 1 });
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "LEADERBOARD"
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-me",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    serveGameplay(session, question);
    let fullStandingsCalled = false;
    server.use(
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            currentQuestionId: question.questionId,
            currentPhase: "LEADERBOARD"
          })
        )
      ),
      http.get(`/api/v1/sessions/${session.sessionId}/results`, () => {
        fullStandingsCalled = true;
        return HttpResponse.json(apiError("auth.unauthorized", "Host only"), { status: 401 });
      }),
      http.get(
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/result`,
        () =>
          HttpResponse.json(
            participantResultResponse({
              sessionId: session.sessionId,
              participantId: record.participantId,
              rank: 7,
              score: 640
            })
          )
      ),
      http.get(
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/rank-context`,
        () =>
          HttpResponse.json(
            participantRankContextResponse({
              sessionId: session.sessionId,
              participantId: record.participantId,
              rank: 7,
              score: 640,
              ahead: { displayName: "Amelia", rank: 6, scoreDifference: 120 },
              behind: { displayName: "David", rank: 8, scoreDifference: 80 }
            })
          )
      )
    );

    renderApp(`/play/${PIN}`);

    expect(await screen.findByText("Ahead of you")).toBeInTheDocument();
    expect(
      screen.getByText(/6th · Amelia · 120 points\s*ahead/)
    ).toBeInTheDocument();
    expect(screen.getByText("Behind you")).toBeInTheDocument();
    expect(
      screen.getByText(/8th · David · 80 points\s*behind/)
    ).toBeInTheDocument();
    // Never the full leaderboard, and never another participant's context.
    expect(fullStandingsCalled).toBe(false);
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("shows You are leading with only the participant behind, for first place", async () => {
    const question = currentQuestionResponse({ phase: "LEADERBOARD" });
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "LEADERBOARD"
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-me",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    serveGameplay(session, question);
    server.use(
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            currentQuestionId: question.questionId,
            currentPhase: "LEADERBOARD"
          })
        )
      ),
      http.get(
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/result`,
        () =>
          HttpResponse.json(
            participantResultResponse({
              sessionId: session.sessionId,
              participantId: record.participantId,
              rank: 1,
              score: 900
            })
          )
      ),
      http.get(
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/rank-context`,
        () =>
          HttpResponse.json(
            participantRankContextResponse({
              sessionId: session.sessionId,
              participantId: record.participantId,
              rank: 1,
              score: 900,
              behind: { displayName: "Ben", rank: 2, scoreDifference: 100 }
            })
          )
      )
    );

    renderApp(`/play/${PIN}`);

    expect(await screen.findByText("You are leading")).toBeInTheDocument();
    expect(screen.getByText("Behind you")).toBeInTheDocument();
    expect(screen.queryByText("Ahead of you")).not.toBeInTheDocument();
  });

  it("shows only the participant ahead for last place", async () => {
    const question = currentQuestionResponse({ phase: "LEADERBOARD" });
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "LEADERBOARD"
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-me",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    serveGameplay(session, question);
    server.use(
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            currentQuestionId: question.questionId,
            currentPhase: "LEADERBOARD"
          })
        )
      ),
      http.get(
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/result`,
        () =>
          HttpResponse.json(
            participantResultResponse({
              sessionId: session.sessionId,
              participantId: record.participantId,
              rank: 5,
              score: 50
            })
          )
      ),
      http.get(
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/rank-context`,
        () =>
          HttpResponse.json(
            participantRankContextResponse({
              sessionId: session.sessionId,
              participantId: record.participantId,
              rank: 5,
              score: 50,
              ahead: { displayName: "Cara", rank: 4, scoreDifference: 40 }
            })
          )
      )
    );

    renderApp(`/play/${PIN}`);

    expect(await screen.findByText("Ahead of you")).toBeInTheDocument();
    expect(screen.queryByText("Behind you")).not.toBeInTheDocument();
    expect(screen.queryByText("You are leading")).not.toBeInTheDocument();
  });

  it("shows Tied with <name> instead of a false ahead or behind", async () => {
    const question = currentQuestionResponse({ phase: "LEADERBOARD" });
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "LEADERBOARD"
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-me",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    serveGameplay(session, question);
    server.use(
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            currentQuestionId: question.questionId,
            currentPhase: "LEADERBOARD"
          })
        )
      ),
      http.get(
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/result`,
        () =>
          HttpResponse.json(
            participantResultResponse({
              sessionId: session.sessionId,
              participantId: record.participantId,
              rank: 2,
              score: 300
            })
          )
      ),
      http.get(
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/rank-context`,
        () =>
          HttpResponse.json(
            participantRankContextResponse({
              sessionId: session.sessionId,
              participantId: record.participantId,
              rank: 2,
              score: 300,
              tiedWith: { displayName: "Priya", rank: 1 }
            })
          )
      )
    );

    renderApp(`/play/${PIN}`);

    expect(await screen.findByText("Tied with Priya")).toBeInTheDocument();
    expect(screen.queryByText("Ahead of you")).not.toBeInTheDocument();
    expect(screen.queryByText("Behind you")).not.toBeInTheDocument();
  });

  it("shows the waiting-for-announcement screen after the final question, and it survives a fresh mount", async () => {
    const session = sessionSummary({
      state: "FINISHED",
      currentQuestionId: undefined,
      finalResultsReleased: false
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-me",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    let personalResultCalled = false;
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}`, () => HttpResponse.json(session)),
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            sessionState: "FINISHED",
            currentPhase: undefined
          })
        )
      ),
      http.get(
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/result`,
        () => {
          personalResultCalled = true;
          return HttpResponse.json(
            apiError("session.results.not-available", "Results are not available"),
            { status: 409 }
          );
        }
      )
    );

    const firstMount = renderApp(`/play/${PIN}`);

    expect(await screen.findByText(/quiz complete/i)).toBeInTheDocument();
    expect(screen.getByText(/winners are being announced/i)).toBeInTheDocument();
    expect(screen.getByText(/watch the shared screen/i)).toBeInTheDocument();
    // No rank, no name, no score leaks while pending.
    expect(screen.queryByText("You finished")).not.toBeInTheDocument();
    expect(screen.queryByText("Winner")).not.toBeInTheDocument();
    // The client never even attempts the gated read while pending — the
    // FSM excludes FINAL_RESULTS_PENDING from personalResult's enabled phases.
    expect(personalResultCalled).toBe(false);

    // A fresh mount (refresh/reconnect) re-derives the same phase from the
    // session summary — never a rank flashes before release.
    firstMount.unmount();
    renderApp(`/play/${PIN}`);
    expect(await screen.findByText(/quiz complete/i)).toBeInTheDocument();
    expect(screen.getByText(/winners are being announced/i)).toBeInTheDocument();
  });

  it("reveals the labeled final result only after the host releases it", async () => {
    const holder = {
      session: sessionSummary({
        state: "FINISHED",
        currentQuestionId: undefined,
        finalResultsReleased: false
      })
    };
    const record = {
      sessionId: holder.session.sessionId!,
      participantId: "participant-me",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    server.use(
      http.get(`/api/v1/sessions/${holder.session.sessionId}`, () =>
        HttpResponse.json(holder.session)
      ),
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: holder.session.sessionId,
            participantId: record.participantId,
            sessionState: "FINISHED",
            currentPhase: undefined
          })
        )
      ),
      http.get(
        `/api/v1/sessions/${holder.session.sessionId}/participants/${record.participantId}/result`,
        () =>
          holder.session.finalResultsReleased
            ? HttpResponse.json(
                participantResultResponse({
                  sessionId: holder.session.sessionId,
                  participantId: record.participantId,
                  state: "FINISHED",
                  currentPhase: undefined,
                  rank: 2,
                  score: 8_450
                })
              )
            : HttpResponse.json(
                apiError("session.results.not-available", "Results are not available"),
                { status: 409 }
              )
      )
    );
    const { client, fake } = fakeRealtimeClient();

    renderApp(`/play/${PIN}`, { realtimeClient: client });
    expect(await screen.findByText(/winners are being announced/i)).toBeInTheDocument();
    act(() => fake.simulateConnect());

    holder.session = { ...holder.session, finalResultsReleased: true };
    act(() => {
      fake.deliver(
        sessionTopic(holder.session.sessionId!),
        protocolMessage("final.results.revealed", holder.session.sessionId!, {})
      );
    });

    expect(await screen.findByText("Winner")).toBeInTheDocument();
    expect(screen.getByText("You finished")).toBeInTheDocument();
    expect(screen.getByText("2nd")).toBeInTheDocument();
    expect(screen.queryByText(/winners are being announced/i)).not.toBeInTheDocument();
  });

  it("labels 4th and 5th place Runner-up, and 6th+ with no label at all", async () => {
    const session = sessionSummary({
      state: "FINISHED",
      currentQuestionId: undefined,
      finalResultsReleased: true
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-me",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}`, () => HttpResponse.json(session)),
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            sessionState: "FINISHED",
            currentPhase: undefined
          })
        )
      ),
      http.get(
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/result`,
        () =>
          HttpResponse.json(
            participantResultResponse({
              sessionId: session.sessionId,
              participantId: record.participantId,
              state: "FINISHED",
              currentPhase: undefined,
              rank: 5,
              score: 60
            })
          )
      )
    );

    renderApp(`/play/${PIN}`);

    expect(await screen.findByText("Runner-up")).toBeInTheDocument();
    expect(screen.queryByText("Winner")).not.toBeInTheDocument();
    expect(screen.getByText("5th")).toBeInTheDocument();
  });

  it("refreshes the personal result when a leaderboard.updated broadcast arrives", async () => {
    const question = currentQuestionResponse({ phase: "LEADERBOARD" });
    const holder = {
      session: sessionSummary({
        sessionId: question.sessionId,
        state: "IN_PROGRESS",
        currentQuestionId: question.questionId,
        currentPhase: "LEADERBOARD" as const
      }),
      score: 320
    };
    const record = {
      sessionId: holder.session.sessionId!,
      participantId: "participant-me",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    server.use(
      http.get(`/api/v1/sessions/${holder.session.sessionId}`, () =>
        HttpResponse.json(holder.session)
      ),
      http.get(`/api/v1/sessions/${holder.session.sessionId}/questions/current`, () =>
        HttpResponse.json(question)
      ),
      http.get(
        `/api/v1/sessions/${holder.session.sessionId}/participants/${record.participantId}/result`,
        () =>
          HttpResponse.json(
            participantResultResponse({
              sessionId: holder.session.sessionId,
              participantId: record.participantId,
              rank: 1,
              score: holder.score
            })
          )
      ),
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: holder.session.sessionId,
            participantId: record.participantId,
            currentQuestionId: question.questionId,
            currentPhase: "LEADERBOARD"
          })
        )
      )
    );
    const { client, fake } = fakeRealtimeClient();

    renderApp(`/play/${PIN}`, { realtimeClient: client });
    expect(await screen.findByText("320")).toBeInTheDocument();
    act(() => fake.simulateConnect());

    // The push (a pure notification — its rows are empty) says results
    // changed; the personal refetch learns the new truth.
    holder.score = 820;
    act(() => {
      fake.deliver(
        sessionTopic(holder.session.sessionId!),
        protocolMessage("leaderboard.updated", holder.session.sessionId!, {
          entries: []
        })
      );
    });

    expect(await screen.findByText("820")).toBeInTheDocument();
    // Two consecutive personal snapshots make a points delta.
    expect(screen.getByText("+500 points")).toBeInTheDocument();
  });

  it("recovers the completion screen on a fresh mount after the session finished", async () => {
    const session = sessionSummary({ state: "FINISHED", currentQuestionId: undefined });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-me",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}`, () => HttpResponse.json(session)),
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            sessionState: "FINISHED",
            currentPhase: undefined
          })
        )
      ),
      http.get(
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/result`,
        () =>
          HttpResponse.json(
            participantResultResponse({
              sessionId: session.sessionId,
              participantId: record.participantId,
              state: "FINISHED",
              currentPhase: undefined,
              rank: 2,
              score: 320
            })
          )
      )
    );

    renderApp(`/play/${PIN}`);

    expect(await screen.findByText(/quiz complete/i)).toBeInTheDocument();
    // The personal finish line — and nothing about anyone else.
    expect(screen.getByText("You finished")).toBeInTheDocument();
    expect(screen.getByText("2nd")).toBeInTheDocument();
    expect(screen.getByText(/final score/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /play another quiz/i })).toBeInTheDocument();
    expect(screen.queryByLabelText("Podium")).not.toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.queryByText("Ann")).not.toBeInTheDocument();
  });

  it("offers only English and Hindi, with a neutral name placeholder", () => {
    renderApp("/play");

    const nameField = screen.getByLabelText(/your name/i);
    expect(nameField).toHaveAttribute("placeholder", "Type your name");
    // A placeholder is a hint, never a value that could be submitted.
    expect(nameField).toHaveValue("");
    const languageSelect = screen.getByLabelText(/language/i);
    const options = within(languageSelect).getAllByRole("option");
    expect(options.map((option) => option.textContent)).toEqual(["English", "हिन्दी"]);
  });

  it("shows the authoritative quiz title during play and on the final screen", async () => {
    const question = currentQuestionResponse();
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "QUESTION_OPEN",
      quizTitle: "BELC Bible Quiz — Gospel of Mark"
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-me",
      guestParticipantToken: "guest-token-2",
      displayName: "Priya",
      preferredLanguage: "hi"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    serveGameplay(session, question);
    server.use(
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            currentQuestionId: question.questionId,
            currentPhase: "QUESTION_OPEN",
            submittedOptionIds: []
          })
        )
      )
    );

    renderApp(`/play/${PIN}`);

    // The title comes from the participant-safe session summary — the
    // participant client never calls a host-only quiz endpoint.
    expect(
      await screen.findByText("BELC Bible Quiz — Gospel of Mark")
    ).toBeInTheDocument();
    expect(await screen.findByText(question.localizations![0].prompt!)).toBeInTheDocument();
    expect(document.title).toBe("BELC Bible Quiz — Gospel of Mark | BELC Family Quiz Platform");
  });

  it("keeps the quiz title on the recovered final screen", async () => {
    const session = sessionSummary({
      state: "FINISHED",
      currentQuestionId: undefined,
      quizTitle: "BELC Bible Quiz — Gospel of Mark"
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-me",
      guestParticipantToken: "guest-token-3",
      displayName: "Aman",
      preferredLanguage: "en"
    };
    usePlayerSessionStore.getState().record(PIN, record);
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}`, () => HttpResponse.json(session)),
      http.post("/api/v1/sessions/reconnect", () =>
        HttpResponse.json(
          sessionSnapshotResponse({
            sessionId: session.sessionId,
            participantId: record.participantId,
            sessionState: "FINISHED",
            currentPhase: undefined
          })
        )
      ),
      http.get(
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/result`,
        () =>
          HttpResponse.json(
            participantResultResponse({
              sessionId: session.sessionId,
              participantId: record.participantId,
              state: "FINISHED",
              currentPhase: undefined,
              rank: 2,
              score: 2032
            })
          )
      )
    );

    renderApp(`/play/${PIN}`);

    expect(await screen.findByText(/quiz complete/i)).toBeInTheDocument();
    expect(screen.getByText("BELC Bible Quiz — Gospel of Mark")).toBeInTheDocument();
    expect(screen.getByText("You finished")).toBeInTheDocument();
    expect(screen.getByText("2nd")).toBeInTheDocument();
  });
});
