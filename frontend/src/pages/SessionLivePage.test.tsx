import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { sessionTopic } from "@/realtime/SessionSubscriptions";
import { fakeRealtimeClient, protocolMessage } from "@/test/fakeStomp";
import { currentQuestionResponse } from "@/test/gameplayFixtures";
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

  it("advances from a closed question by chaining reveal, leaderboard, and advance", async () => {
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
    const calls: string[] = [];
    server.use(
      http.post(`/api/v1/sessions/${session.sessionId}/questions/reveal`, () => {
        calls.push("reveal");
        return HttpResponse.json({ ...session, currentPhase: "ANSWER_REVEALED" });
      }),
      http.post(`/api/v1/sessions/${session.sessionId}/leaderboard`, () => {
        calls.push("leaderboard");
        return HttpResponse.json({ entries: [] });
      }),
      http.post(`/api/v1/sessions/${session.sessionId}/questions/advance`, () => {
        calls.push("advance");
        return HttpResponse.json({ ...session, state: "FINISHED", currentPhase: undefined });
      })
    );
    const user = userEvent.setup();

    renderApp(`/sessions/${session.sessionId}/play`);
    await user.click(await screen.findByRole("button", { name: /next question/i }));

    await waitFor(() => expect(calls).toEqual(["reveal", "leaderboard", "advance"]));
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
    await user.click(await screen.findByRole("button", { name: /next question/i }));

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

    expect(await screen.findByRole("button", { name: /next question/i })).toBeInTheDocument();
  });

  it("shows a completion message once the session has finished", async () => {
    signIn();
    const session = sessionSummary({ state: "FINISHED" });
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, undefined);

    renderApp(`/sessions/${session.sessionId}/play`);

    expect(await screen.findByText(/session has finished/i)).toBeInTheDocument();
  });
});
