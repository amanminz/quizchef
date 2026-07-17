import { act, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { sessionTopic } from "@/realtime/SessionSubscriptions";
import { fakeRealtimeClient, protocolMessage } from "@/test/fakeStomp";
import {
  currentQuestionResponse,
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
    const trueOption = screen.getByRole("button", { name: "True" });
    expect(trueOption).toBeDisabled();
  });

  it("starts the first question from the countdown", async () => {
    signIn();
    const holder = {
      session: sessionSummary({ state: "IN_PROGRESS", currentQuestionId: undefined }),
      question: undefined as CurrentQuestionResponse | undefined
    };
    serveQuiz(holder.session.publishedQuizVersionId!);
    server.use(
      http.get(`/api/v1/sessions/${holder.session.sessionId}`, () => HttpResponse.json(holder.session)),
      http.get(`/api/v1/sessions/${holder.session.sessionId}/questions/current`, () =>
        holder.question
          ? HttpResponse.json(holder.question)
          : HttpResponse.json(apiError("session.no-current-question", "No question"), { status: 409 })
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
      http.get(`/api/v1/sessions/${holder.session.sessionId}`, () => HttpResponse.json(holder.session)),
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

  it("recovers the full final results on a fresh mount after the session finished", async () => {
    // The refresh-recovery case: no realtime events ever arrive — everything
    // renders from the session summary and the public results read alone.
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

    expect(await screen.findByText(/quiz complete/i)).toBeInTheDocument();
    // Winner and podium reflect the server's rank 1, verbatim ("Winner"
    // appears on both the winner card and the summary row — both expected).
    expect(screen.getAllByText("Winner").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Ann").length).toBeGreaterThan(0);
    // Final standings table and the host's what-next actions.
    expect(screen.getByRole("table")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /host another session/i })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /return to dashboard/i })).toBeInTheDocument();
  });
});
