import { act, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { sessionTopic } from "@/realtime/SessionSubscriptions";
import { fakeRealtimeClient, protocolMessage } from "@/test/fakeStomp";
import {
  currentQuestionResponse,
  leaderboardEntry,
  revealedQuestionResponse,
  sessionResultsResponse
} from "@/test/gameplayFixtures";
import { apiError, testIdentity } from "@/test/handlers";
import { quizResponse } from "@/test/quizFixtures";
import { sessionSummary } from "@/test/sessionFixtures";
import { server } from "@/test/server";
import { currentPath, renderApp } from "@/test/testUtils";
import type { CurrentQuestionResponse, SessionSummaryResponse } from "@/types/api";

function signIn() {
  useAuthStore.setState({ token: testIdentity.token });
}

function serveQuiz(quizId: string) {
  server.use(
    http.get(`/api/v1/quizzes/${quizId}`, () =>
      HttpResponse.json(
        quizResponse({
          id: quizId,
          state: "PUBLISHED",
          localizations: [{ languageCode: "en", title: "Bible Quiz" }]
        })
      )
    )
  );
}

function serveGameplay(
  session: SessionSummaryResponse,
  question: CurrentQuestionResponse | undefined
) {
  server.use(
    http.get(`/api/v1/sessions/${session.sessionId}`, () => HttpResponse.json(session)),
    http.get(`/api/v1/sessions/${session.sessionId}/questions/current`, () =>
      question
        ? HttpResponse.json(question)
        : HttpResponse.json(apiError("session.no-current-question", "No question is in play"), {
            status: 409
          })
    )
  );
}

describe("SessionLivePage", () => {
  it("monitors the open question read-only, with no way to answer", async () => {
    signIn();
    const question = currentQuestionResponse();
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "QUESTION_OPEN",
      participantCount: 3
    });
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, question);

    renderApp(`/sessions/${session.sessionId}/play`);

    expect(await screen.findByText(question.localizations![0].prompt!)).toBeInTheDocument();
    expect(screen.getByText("3 participants")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /submit answer/i })).not.toBeInTheDocument();
    // The host's projection lists the options — nothing selectable.
    expect(screen.getByText("True")).toBeInTheDocument();
    expect(screen.getByText("False")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "True" })).not.toBeInTheDocument();
  });

  it("shows the backend's answer progress and highlights when everyone answered", async () => {
    signIn();
    const question = currentQuestionResponse();
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "QUESTION_OPEN",
      participantCount: 3
    });
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, question);
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}/answer-progress`, () =>
        HttpResponse.json({
          sessionId: session.sessionId,
          questionId: question.questionId,
          answeredCount: 3,
          eligibleCount: 3
        })
      )
    );

    renderApp(`/sessions/${session.sessionId}/play`);

    expect(await screen.findByText("3 / 3 answered")).toBeInTheDocument();
    // Everyone's in — the close-early transition is emphasized, never auto-fired.
    expect(screen.getByRole("button", { name: /close question/i })).toBeInTheDocument();
  });

  it("renders English and Hindi together for the host when both exist", async () => {
    signIn();
    const base = currentQuestionResponse();
    const question: CurrentQuestionResponse = {
      ...base,
      localizations: [
        ...base.localizations!,
        {
          languageCode: "hi",
          prompt: "योना को एक बड़ी मछली ने निगल लिया था।",
          optionTexts: [
            { optionId: base.options![0].optionId!, text: "सही" },
            { optionId: base.options![1].optionId!, text: "गलत" }
          ]
        }
      ]
    };
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "QUESTION_OPEN"
    });
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, question);

    renderApp(`/sessions/${session.sessionId}/play`);

    expect(await screen.findByText(base.localizations![0].prompt!)).toBeInTheDocument();
    expect(screen.getByText("योना को एक बड़ी मछली ने निगल लिया था।")).toBeInTheDocument();
    expect(screen.getByText("सही")).toBeInTheDocument();
    expect(
      screen.queryByText(/Hindi translation unavailable/i)
    ).not.toBeInTheDocument();
  });

  it("notes a missing Hindi translation instead of leaving a gap", async () => {
    signIn();
    const question = currentQuestionResponse();
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "QUESTION_OPEN"
    });
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, question);

    renderApp(`/sessions/${session.sessionId}/play`);

    expect(await screen.findByText(question.localizations![0].prompt!)).toBeInTheDocument();
    expect(
      screen.getByText("Hindi translation unavailable for this question.")
    ).toBeInTheDocument();
  });

  it("starts the first question from the countdown", async () => {
    signIn();
    const holder = {
      session: sessionSummary({ state: "IN_PROGRESS", currentQuestionId: undefined }),
      question: undefined as CurrentQuestionResponse | undefined
    };
    serveQuiz(holder.session.publishedQuizVersionId!);
    server.use(
      http.get(`/api/v1/sessions/${holder.session.sessionId}`, () =>
        HttpResponse.json(holder.session)
      ),
      http.get(`/api/v1/sessions/${holder.session.sessionId}/questions/current`, () =>
        holder.question
          ? HttpResponse.json(holder.question)
          : HttpResponse.json(apiError("session.no-current-question", "No question"), {
              status: 409
            })
      ),
      http.post(`/api/v1/sessions/${holder.session.sessionId}/questions/start`, () => {
        holder.question = currentQuestionResponse({ sessionId: holder.session.sessionId });
        holder.session = {
          ...holder.session,
          currentQuestionId: holder.question.questionId,
          currentPhase: "QUESTION_OPEN"
        };
        return HttpResponse.json(holder.session);
      })
    );
    const user = userEvent.setup();

    renderApp(`/sessions/${holder.session.sessionId}/play`);
    expect(await screen.findByText(/get ready/i)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /start question/i }));

    expect(await screen.findByText(/question 1 of/i)).toBeInTheDocument();
  });

  it("steps through reveal, leaderboard, and next question one server command at a time", async () => {
    signIn();
    const question = currentQuestionResponse({ phase: "QUESTION_CLOSED", questionNumber: 1 });
    const holder = {
      session: sessionSummary({
        sessionId: question.sessionId,
        state: "IN_PROGRESS",
        currentQuestionId: question.questionId,
        currentPhase: "QUESTION_CLOSED" as const
      }),
      question
    };
    serveQuiz(holder.session.publishedQuizVersionId!);
    const calls: string[] = [];
    server.use(
      http.get(`/api/v1/sessions/${holder.session.sessionId}`, () =>
        HttpResponse.json(holder.session)
      ),
      http.get(`/api/v1/sessions/${holder.session.sessionId}/questions/current`, () =>
        HttpResponse.json(holder.question)
      ),
      http.get(`/api/v1/sessions/${holder.session.sessionId}/results`, () =>
        HttpResponse.json(
          sessionResultsResponse({
            sessionId: holder.session.sessionId,
            currentPhase: holder.session.currentPhase
          })
        )
      ),
      http.post(`/api/v1/sessions/${holder.session.sessionId}/questions/reveal`, () => {
        calls.push("reveal");
        holder.session = { ...holder.session, currentPhase: "ANSWER_REVEALED" };
        holder.question = revealedQuestionResponse(question);
        return HttpResponse.json(holder.session);
      }),
      http.post(`/api/v1/sessions/${holder.session.sessionId}/leaderboard`, () => {
        calls.push("leaderboard");
        holder.session = { ...holder.session, currentPhase: "LEADERBOARD" };
        holder.question = { ...holder.question, phase: "LEADERBOARD" };
        return HttpResponse.json({ entries: [] });
      })
    );
    const user = userEvent.setup();

    renderApp(`/sessions/${holder.session.sessionId}/play`);

    // Closed → one click issues exactly the reveal command.
    await user.click(await screen.findByRole("button", { name: /reveal answer/i }));
    expect(calls).toEqual(["reveal"]);
    // The reveal screen: the server's correct option and the explanation.
    expect(await screen.findByText("Correct answer")).toBeInTheDocument();
    expect(screen.getByText(/jonah 1:17 tells the story/i)).toBeInTheDocument();

    // Revealed → one click issues exactly the leaderboard command.
    await user.click(screen.getByRole("button", { name: /show leaderboard/i }));
    expect(calls).toEqual(["reveal", "leaderboard"]);
    // The standings render the server's rows verbatim.
    expect(await screen.findByText("Ann")).toBeInTheDocument();
    expect(screen.getByText("750")).toBeInTheDocument();
    // Question 1 of 2 → the next advance is a plain next question.
    expect(screen.getByRole("button", { name: /next question/i })).toBeInTheDocument();
  });

  it("labels the last advance Finish Quiz", async () => {
    signIn();
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
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, question);
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}/results`, () =>
        HttpResponse.json(
          sessionResultsResponse({ sessionId: session.sessionId, currentPhase: "LEADERBOARD" })
        )
      )
    );

    renderApp(`/sessions/${session.sessionId}/play`);

    expect(await screen.findByRole("button", { name: /finish quiz/i })).toBeInTheDocument();
  });

  it("surfaces an authorization failure without leaving the page", async () => {
    signIn();
    const question = currentQuestionResponse({ phase: "QUESTION_CLOSED" });
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "QUESTION_CLOSED"
    });
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, question);
    server.use(
      http.post(`/api/v1/sessions/${session.sessionId}/questions/reveal`, () =>
        HttpResponse.json(apiError("auth.forbidden", "You are not the host"), { status: 403 })
      )
    );
    const user = userEvent.setup();

    renderApp(`/sessions/${session.sessionId}/play`);
    await user.click(await screen.findByRole("button", { name: /reveal answer/i }));

    expect(await screen.findByText(/you are not the host/i)).toBeInTheDocument();
    expect(currentPath()).toBe(`/sessions/${session.sessionId}/play`);
  });

  it("reflects a remote question.closed event without any host action", async () => {
    signIn();
    const question = currentQuestionResponse();
    const holder = {
      session: sessionSummary({
        sessionId: question.sessionId,
        state: "IN_PROGRESS",
        currentQuestionId: question.questionId,
        currentPhase: "QUESTION_OPEN"
      })
    };
    serveQuiz(holder.session.publishedQuizVersionId!);
    server.use(
      http.get(`/api/v1/sessions/${holder.session.sessionId}`, () =>
        HttpResponse.json(holder.session)
      ),
      http.get(`/api/v1/sessions/${holder.session.sessionId}/questions/current`, () =>
        HttpResponse.json({ ...question, phase: holder.session.currentPhase })
      )
    );
    const { client, fake } = fakeRealtimeClient();

    renderApp(`/sessions/${holder.session.sessionId}/play`, { realtimeClient: client });
    await screen.findByText(question.localizations![0].prompt!);
    act(() => fake.simulateConnect());

    holder.session = { ...holder.session, currentPhase: "QUESTION_CLOSED" };
    act(() => {
      fake.deliver(
        sessionTopic(holder.session.sessionId!),
        protocolMessage("question.closed", holder.session.sessionId!, {
          questionId: question.questionId
        })
      );
    });

    expect(await screen.findByRole("button", { name: /reveal answer/i })).toBeInTheDocument();
  });

  it("recovers the completed final results on a fresh mount, without replaying the reveal", async () => {
    // The refresh-recovery case: no realtime events ever arrive — everything
    // renders from the session summary and the host's results read alone,
    // and the podium ceremony (already played once) does not re-run.
    signIn();
    const session = sessionSummary({ state: "FINISHED" });
    sessionStorage.setItem(`quizchef.podium-played.${session.sessionId}`, "played");
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, undefined);
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}/results`, () =>
        HttpResponse.json(
          sessionResultsResponse({
            sessionId: session.sessionId,
            state: "FINISHED",
            currentPhase: undefined
          })
        )
      )
    );

    renderApp(`/sessions/${session.sessionId}/play`);

    expect(await screen.findByText(/quiz complete/i)).toBeInTheDocument();
    // The completed podium reflects the server's ranks, verbatim.
    expect(screen.getByLabelText("Podium")).toBeInTheDocument();
    expect(screen.getAllByText("Ann").length).toBeGreaterThan(0);
    expect(screen.getByRole("link", { name: /host another session/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /return to dashboard/i })).toBeInTheDocument();
    sessionStorage.removeItem(`quizchef.podium-played.${session.sessionId}`);
  });

  it("reveals the winners third, second, then first, then the remaining standings", async () => {
    signIn();
    const session = sessionSummary({ state: "FINISHED" });
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, undefined);
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}/results`, () =>
        HttpResponse.json(
          sessionResultsResponse({
            sessionId: session.sessionId,
            state: "FINISHED",
            currentPhase: undefined,
            entries: [
              leaderboardEntry({ displayName: "Ann", score: 900, rank: 1 }),
              leaderboardEntry({ displayName: "Ben", score: 700, rank: 2 }),
              leaderboardEntry({ displayName: "Cara", score: 500, rank: 3 }),
              leaderboardEntry({ displayName: "Dan", score: 100, rank: 4 })
            ]
          })
        )
      )
    );

    renderApp(`/sessions/${session.sessionId}/play`);

    // Suspense first — no places revealed yet.
    expect(await screen.findByText(/and the winners are/i)).toBeInTheDocument();
    expect(screen.queryByText("Cara")).not.toBeInTheDocument();

    // Third enters before second; second before first.
    expect(await screen.findByText("Cara", undefined, { timeout: 3_000 })).toBeInTheDocument();
    expect(screen.queryByText("Ben")).not.toBeInTheDocument();
    expect(await screen.findByText("Ben", undefined, { timeout: 3_000 })).toBeInTheDocument();
    expect(screen.queryByText("Ann")).not.toBeInTheDocument();
    expect(await screen.findByText("Ann", undefined, { timeout: 3_000 })).toBeInTheDocument();

    // The reveal completes into the podium and the remaining standings.
    expect(
      await screen.findByLabelText("Podium", undefined, { timeout: 4_000 })
    ).toBeInTheDocument();
    expect(screen.getByText("Remaining standings")).toBeInTheDocument();
    expect(screen.getByText("Dan")).toBeInTheDocument();
    sessionStorage.removeItem(`quizchef.podium-played.${session.sessionId}`);
  }, 15_000);

  it("skip shows the final state at once; replay is local-only", async () => {
    signIn();
    const session = sessionSummary({ state: "FINISHED" });
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, undefined);
    let resultReads = 0;
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}/results`, () => {
        resultReads += 1;
        return HttpResponse.json(
          sessionResultsResponse({
            sessionId: session.sessionId,
            state: "FINISHED",
            currentPhase: undefined
          })
        );
      })
    );
    const user = userEvent.setup();

    renderApp(`/sessions/${session.sessionId}/play`);
    await screen.findByText(/and the winners are/i);
    const readsBeforeSkip = resultReads;

    await user.click(screen.getByRole("button", { name: /skip animation/i }));
    expect(await screen.findByLabelText("Podium")).toBeInTheDocument();

    // Replay restarts the local ceremony — no backend read or mutation.
    await user.click(screen.getByRole("button", { name: /replay podium/i }));
    expect(await screen.findByText(/and the winners are/i)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /skip animation/i }));
    expect(await screen.findByLabelText("Podium")).toBeInTheDocument();
    expect(resultReads).toBe(readsBeforeSkip);
    sessionStorage.removeItem(`quizchef.podium-played.${session.sessionId}`);
  });

  it("reveals without staging under prefers-reduced-motion", async () => {
    // Reduced motion: same content and ordering, no ceremony, no waiting.
    const originalMatchMedia = window.matchMedia;
    window.matchMedia = (query: string) =>
      ({
        matches: query.includes("prefers-reduced-motion"),
        media: query,
        onchange: null,
        addEventListener: () => undefined,
        removeEventListener: () => undefined,
        addListener: () => undefined,
        removeListener: () => undefined,
        dispatchEvent: () => false
      }) as MediaQueryList;
    signIn();
    const session = sessionSummary({ state: "FINISHED" });
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, undefined);
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}/results`, () =>
        HttpResponse.json(
          sessionResultsResponse({
            sessionId: session.sessionId,
            state: "FINISHED",
            currentPhase: undefined
          })
        )
      )
    );

    renderApp(`/sessions/${session.sessionId}/play`);

    // Straight to the completed podium — no suspense screen.
    expect(await screen.findByLabelText("Podium")).toBeInTheDocument();
    expect(screen.queryByText(/and the winners are/i)).not.toBeInTheDocument();
    window.matchMedia = originalMatchMedia;
    sessionStorage.removeItem(`quizchef.podium-played.${session.sessionId}`);
  });

  it("renders a two-player finish without empty podium placeholders", async () => {
    signIn();
    const session = sessionSummary({ state: "FINISHED" });
    sessionStorage.setItem(`quizchef.podium-played.${session.sessionId}`, "played");
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, undefined);
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}/results`, () =>
        HttpResponse.json(
          sessionResultsResponse({
            sessionId: session.sessionId,
            state: "FINISHED",
            currentPhase: undefined,
            participantCount: 2,
            entries: [
              leaderboardEntry({ displayName: "Ann", score: 900, rank: 1 }),
              leaderboardEntry({ displayName: "Ben", score: 700, rank: 2 })
            ]
          })
        )
      )
    );

    renderApp(`/sessions/${session.sessionId}/play`);

    const podium = await screen.findByLabelText("Podium");
    expect(within(podium).getAllByRole("listitem")).toHaveLength(2);
    expect(screen.queryByText("Remaining standings")).not.toBeInTheDocument();
    sessionStorage.removeItem(`quizchef.podium-played.${session.sessionId}`);
  });
});
