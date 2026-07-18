import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { usePlayerSessionStore } from "@/features/gameplay/playerSessionStore";
import { sessionTopic } from "@/realtime/SessionSubscriptions";
import { fakeRealtimeClient, protocolMessage } from "@/test/fakeStomp";
import {
  currentQuestionResponse,
  participantResultResponse,
  participantSessionResponse,
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
});
