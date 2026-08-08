import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { motivationFor } from "@/features/gameplay/motivation";
import { usePlayerSessionStore } from "@/features/gameplay/playerSessionStore";
import { sessionTopic } from "@/realtime/SessionSubscriptions";
import { fakeRealtimeClient, protocolMessage } from "@/test/fakeStomp";
import {
  currentQuestionResponse,
  finalPlacementResponse,
  participantResultResponse,
  relativePlacementResponse,
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

    expect(await screen.findByText("Not quite this time")).toBeInTheDocument();
  });

  it("shows their own score and encouragement at the leaderboard step, and no rank", async () => {
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
              score: 320,
              pointsEarned: 100
            })
          )
      )
    );
    let fullStandingsCalled = false;

    renderApp(`/play/${PIN}`);

    // Their own numbers and a line of encouragement — and no standing of
    // any kind: no rank, no movement, no neighbour, no table.
    expect(await screen.findByText("320")).toBeInTheDocument();
    expect(screen.getByText(/\+100/)).toBeInTheDocument();
    expect(screen.queryByText("Your rank")).not.toBeInTheDocument();
    expect(screen.queryByText("2nd")).not.toBeInTheDocument();
    expect(screen.queryByText(/up \d+ place|down \d+ place/i)).not.toBeInTheDocument();
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

  it.each([
    ["correct", 0, "en"],
    ["incorrect", 1, "en"],
    ["correct", 0, "hi"],
    ["incorrect", 1, "hi"]
  ] as const)(
    "renders a $# motivation on the participant screen",
    async (outcome, optionIndex, language) => {
      // The regression this exists for: the catalogue was fine and the
      // message still never reached a phone. Asserting the *page* renders
      // it is the only version of this test that would have caught that.
      const base = currentQuestionResponse({ questionNumber: 2, totalQuestions: 4 });
      const question = revealedQuestionResponse(base);
      const session = sessionSummary({
        sessionId: question.sessionId,
        state: "IN_PROGRESS",
        currentQuestionId: question.questionId,
        currentPhase: "ANSWER_REVEALED"
      });
      const record = {
        sessionId: session.sessionId!,
        participantId: "participant-me",
        guestParticipantToken: "guest-token-1",
        displayName: "Aman",
        preferredLanguage: language
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
              submittedOptionIds: [base.options![optionIndex].optionId!]
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
                score: 3420,
                pointsEarned: outcome === "correct" ? 750 : 0
              })
            )
        )
      );

      renderApp(`/play/${PIN}`);

      const expected = motivationFor({
        sessionId: session.sessionId,
        participantId: record.participantId,
        questionNumber: 2,
        outcome,
        language
      });
      expect(await screen.findByText(expected)).toBeVisible();
      // Alongside the feedback it belongs to, not instead of it.
      expect(await screen.findByText("3,420")).toBeInTheDocument();
      // And never with a position of any kind.
      expect(screen.queryByText(/\d+(st|nd|rd|th)\b/)).not.toBeInTheDocument();
    }
  );

  it("shows a motivation for an unanswered question too", async () => {
    const base = currentQuestionResponse({ questionNumber: 3, totalQuestions: 5 });
    const question = revealedQuestionResponse(base);
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "ANSWER_REVEALED"
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
            currentPhase: "ANSWER_REVEALED",
            // Nothing submitted — the timer ran out on them.
            submittedOptionIds: []
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
              score: 2670,
              pointsEarned: 0
            })
          )
      )
    );

    renderApp(`/play/${PIN}`);

    expect(
      await screen.findByText(
        motivationFor({
          sessionId: session.sessionId,
          participantId: record.participantId,
          questionNumber: 3,
          outcome: "unanswered",
          language: "en"
        })
      )
    ).toBeVisible();
    expect(screen.getByText("Time's up")).toBeInTheDocument();
  });

  it("shows the same motivation after a refresh, and still no rank", async () => {
    const base = currentQuestionResponse({ questionNumber: 2, totalQuestions: 4 });
    const question = revealedQuestionResponse(base);
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "ANSWER_REVEALED"
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-me",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
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
            currentPhase: "ANSWER_REVEALED",
            submittedOptionIds: [base.options![0].optionId!]
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
              score: 3420,
              pointsEarned: 750
            })
          )
      )
    );

    const expected = motivationFor({
      sessionId: session.sessionId,
      participantId: record.participantId,
      questionNumber: 2,
      outcome: "correct",
      language: "hi"
    });

    const first = renderApp(`/play/${PIN}`);
    expect(await screen.findByText(expected)).toBeVisible();
    first.unmount();

    // Determinism is what makes a refresh mid-reveal show the same line the
    // player was already reading — nothing is persisted to achieve it.
    renderApp(`/play/${PIN}`);
    expect(await screen.findByText(expected)).toBeVisible();
    expect(screen.queryByText(/\d+(st|nd|rd|th)\b/)).not.toBeInTheDocument();
  });

  it("never asks for ranking neighbours, and shows none, after a non-final question", async () => {
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
    let neighboursCalled = false;
    let topFiveCalled = false;
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
      // The host's projected Top 5 and the retired neighbours endpoint are
      // both stubbed purely so a stray call would be *observable* rather
      // than a network error — neither should ever be reached.
      http.get(`/api/v1/sessions/${session.sessionId}/leaderboard/top-five`, () => {
        topFiveCalled = true;
        return HttpResponse.json(apiError("auth.unauthorized", "Host only"), { status: 401 });
      }),
      http.get(
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/rank-context`,
        () => {
          neighboursCalled = true;
          return HttpResponse.json(apiError("not.found", "Gone"), { status: 404 });
        }
      ),
      http.get(
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/result`,
        () =>
          HttpResponse.json(
            participantResultResponse({
              sessionId: session.sessionId,
              participantId: record.participantId,
              score: 640,
              pointsEarned: 120
            })
          )
      )
    );

    renderApp(`/play/${PIN}`);

    // Their own score renders; nothing about anyone's position does.
    expect(await screen.findByText("640")).toBeInTheDocument();
    expect(screen.queryByText("Ahead of you")).not.toBeInTheDocument();
    expect(screen.queryByText("Behind you")).not.toBeInTheDocument();
    expect(screen.queryByText(/tied with/i)).not.toBeInTheDocument();
    expect(screen.queryByText("Amelia")).not.toBeInTheDocument();
    expect(screen.queryByText("David")).not.toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    // And none of the three standings reads is attempted at all.
    expect(neighboursCalled).toBe(false);
    expect(fullStandingsCalled).toBe(false);
    expect(topFiveCalled).toBe(false);
  });

  it("shows a motivational line matched to the outcome, in the player's language", async () => {
    const question = revealedQuestionResponse(
      currentQuestionResponse({ questionNumber: 1, totalQuestions: 3 })
    );
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "ANSWER_REVEALED"
    });
    const record = {
      sessionId: session.sessionId!,
      participantId: "participant-me",
      guestParticipantToken: "guest-token-1",
      displayName: "Aman",
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
            currentPhase: "ANSWER_REVEALED",
            // They answered wrongly — the gentlest case, and the one that
            // most needs to read well.
            submittedOptionIds: [question.options![1].optionId!]
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
              score: 2670,
              pointsEarned: 0
            })
          )
      )
    );

    renderApp(`/play/${PIN}`);

    // The Hindi catalogue, because that is the language they joined in.
    const message = await screen.findByText(
      motivationFor({
        sessionId: session.sessionId,
        participantId: record.participantId,
        questionNumber: 1,
        outcome: "incorrect",
        language: "hi"
      })
    );
    expect(message).toBeInTheDocument();
    expect(message.textContent).toMatch(/[\u0900-\u097F]/);
    // Encouragement, never a standing.
    expect(screen.queryByText(/rank|place|position/i)).not.toBeInTheDocument();
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
        `/api/v1/sessions/${holder.session.sessionId}/participants/${record.participantId}/final-placement`,
        () =>
          holder.session.finalResultsReleased
            ? HttpResponse.json(
                finalPlacementResponse({
                  sessionId: holder.session.sessionId,
                  participantId: record.participantId,
                  rank: 2,
                  label: "WINNER",
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

  it("renders the label and rank the server assigned, never one it worked out", async () => {
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
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/final-placement`,
        () =>
          HttpResponse.json(
            finalPlacementResponse({
              sessionId: session.sessionId,
              participantId: record.participantId,
              rank: 5,
              label: "RUNNER_UP",
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

  it("gives a player outside the reveal group names and a score, never a position", async () => {
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
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/final-placement`,
        () =>
          HttpResponse.json(
            relativePlacementResponse({
              sessionId: session.sessionId,
              participantId: record.participantId
            })
          )
      )
    );

    renderApp(`/play/${PIN}`);

    expect(await screen.findByText(/quiz complete/i)).toBeInTheDocument();
    expect(screen.getByText("4,210")).toBeInTheDocument();
    expect(
      screen.getByText(/You finished ahead of David and just behind Amelia\./)
    ).toBeInTheDocument();
    // No position of their own, and no label that would imply one.
    expect(screen.queryByText(/\d+(st|nd|rd|th)/)).not.toBeInTheDocument();
    expect(screen.queryByText("You finished")).not.toBeInTheDocument();
    expect(screen.queryByText(/winner|runner-up|finalist/i)).not.toBeInTheDocument();
  });

  it("describes equal scores by the server's ordering, never as a tie", async () => {
    // Two players on the same score are still one ahead of the other —
    // the ranking breaks every tie — so the wording says so. Nothing in
    // the response or the card can call them tied.
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
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/final-placement`,
        () =>
          HttpResponse.json(
            relativePlacementResponse({
              sessionId: session.sessionId,
              participantId: record.participantId,
              // Everyone in this room scored 4,210; the server still put
              // Priya above and David below.
              score: 4210,
              behind: { displayName: "Priya" },
              aheadOf: { displayName: "David" }
            })
          )
      )
    );

    renderApp(`/play/${PIN}`);

    expect(
      await screen.findByText(/You finished ahead of David and just behind Priya\./)
    ).toBeInTheDocument();
    expect(screen.queryByText(/tied|alongside|level with|same as/i)).not.toBeInTheDocument();
  });

  it("waits rather than erroring when the backend predates the placement endpoint", async () => {
    // Staggered deploy, new frontend against an older backend: the two
    // Railway services do not land together. "Results aren't out yet" is
    // the truthful reading of a missing route, and it is the screen the
    // participant was already on.
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
    let attempts = 0;
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
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/final-placement`,
        () => {
          attempts += 1;
          // Spring's fallback for an unrouted request.
          return HttpResponse.json(apiError("http.404", "Not Found"), { status: 404 });
        }
      )
    );

    renderApp(`/play/${PIN}`);

    expect(await screen.findByText(/winners are being announced/i)).toBeInTheDocument();
    // Not an error page, and no rank invented to fill the gap.
    expect(screen.queryByText(/unavailable/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/\d+(st|nd|rd|th)/)).not.toBeInTheDocument();
    // A route that does not exist is not worth retrying.
    await waitFor(() => expect(attempts).toBe(1));
  });

  it("renders an older backend's response shape without inventing a rank", async () => {
    // The same staggered window from the other side: an older backend
    // still sends `rank` on the progress read and omits `pointsEarned`.
    // The new client must ignore the former completely and survive the
    // latter — a rank must not reappear just because one is on the wire.
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
          HttpResponse.json({
            sessionId: session.sessionId,
            state: "IN_PROGRESS",
            currentPhase: "LEADERBOARD",
            totalQuestions: 2,
            participantCount: 6,
            participantId: record.participantId,
            displayName: "Aman",
            // The old shape, verbatim: a rank, and no pointsEarned.
            rank: 4,
            score: 640
          })
      )
    );

    renderApp(`/play/${PIN}`);

    expect(await screen.findByText("640")).toBeInTheDocument();
    // The rank on the wire is never read, so it cannot be rendered.
    expect(screen.queryByText("4th")).not.toBeInTheDocument();
    expect(screen.queryByText(/your rank/i)).not.toBeInTheDocument();
    // A missing points figure omits the line instead of showing "+undefined".
    expect(screen.queryByText(/\+\s*(undefined|NaN)/)).not.toBeInTheDocument();
    // And the encouragement still renders, so the card is never empty.
    expect(
      screen.getByText(
        motivationFor({
          sessionId: session.sessionId,
          participantId: record.participantId,
          questionNumber: 1,
          outcome: "unanswered",
          language: "en"
        })
      )
    ).toBeInTheDocument();
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
      score: 320,
      pointsEarned: 100
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
              score: holder.score,
              pointsEarned: holder.pointsEarned
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
    holder.pointsEarned = 500;
    act(() => {
      fake.deliver(
        sessionTopic(holder.session.sessionId!),
        protocolMessage("leaderboard.updated", holder.session.sessionId!, {
          entries: []
        })
      );
    });

    expect(await screen.findByText("820")).toBeInTheDocument();
    // The points are the server's own number for this question, not a
    // diff of two renders — which is why they survive a refresh.
    expect(screen.getByText(/\+500/)).toBeInTheDocument();
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
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/final-placement`,
        () =>
          HttpResponse.json(
            finalPlacementResponse({
              sessionId: session.sessionId,
              participantId: record.participantId,
              rank: 2,
              label: "WINNER",
              score: 320
            })
          )
      )
    );

    renderApp(`/play/${PIN}`);

    // The personal finish line — and nothing about anyone else.
    expect(await screen.findByText("You finished")).toBeInTheDocument();
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
        `/api/v1/sessions/${session.sessionId}/participants/${record.participantId}/final-placement`,
        () =>
          HttpResponse.json(
            finalPlacementResponse({
              sessionId: session.sessionId,
              participantId: record.participantId,
              rank: 2,
              label: "WINNER",
              score: 2032
            })
          )
      )
    );

    renderApp(`/play/${PIN}`);

    expect(await screen.findByText("You finished")).toBeInTheDocument();
    expect(screen.getByText("BELC Bible Quiz — Gospel of Mark")).toBeInTheDocument();
    expect(screen.getByText("2nd")).toBeInTheDocument();
  });
});
