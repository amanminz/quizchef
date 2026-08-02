import { act, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";
import { useAuthStore } from "@/auth/authStore";
import { usePresentationStore } from "@/features/sessions/presentationStore";
import { sessionTopic } from "@/realtime/SessionSubscriptions";
import { fakeRealtimeClient, protocolMessage } from "@/test/fakeStomp";
import {
  answerDistributionResponse,
  currentQuestionResponse,
  leaderboardEntry,
  previewQuestionResponse,
  revealedQuestionResponse,
  sessionResultsResponse,
  topFiveTransitionResponse
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

  it("shows the reading period with the prompt and no options, and no answer-progress badge", async () => {
    signIn();
    const base = currentQuestionResponse();
    const previewing = previewQuestionResponse(base);
    const session = sessionSummary({
      sessionId: previewing.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: previewing.questionId,
      currentPhase: "QUESTION_PREVIEW"
    });
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, previewing);

    renderApp(`/sessions/${session.sessionId}/play`);

    expect(await screen.findByText(base.localizations![0].prompt!)).toBeInTheDocument();
    expect(screen.getByText(/read the question/i)).toBeInTheDocument();
    expect(screen.queryByText("True")).not.toBeInTheDocument();
    expect(screen.queryByText("False")).not.toBeInTheDocument();
    expect(screen.queryByText(/answered/i)).not.toBeInTheDocument();
  });

  it("shows both language prompts during a bilingual reading period, with no options in either", async () => {
    signIn();
    const base = currentQuestionResponse();
    const bilingual: CurrentQuestionResponse = {
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
    const previewing = previewQuestionResponse(bilingual);
    const session = sessionSummary({
      sessionId: previewing.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: previewing.questionId,
      currentPhase: "QUESTION_PREVIEW"
    });
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, previewing);

    renderApp(`/sessions/${session.sessionId}/play`);

    expect(await screen.findByText(base.localizations![0].prompt!)).toBeInTheDocument();
    expect(screen.getByText("योना को एक बड़ी मछली ने निगल लिया था।")).toBeInTheDocument();
    expect(screen.queryByText("सही")).not.toBeInTheDocument();
    expect(screen.queryByText("गलत")).not.toBeInTheDocument();
  });

  it("moves from the reading period to open options on the question.started event, never sooner", async () => {
    signIn();
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
    serveQuiz(holder.session.publishedQuizVersionId!);
    server.use(
      http.get(`/api/v1/sessions/${holder.session.sessionId}`, () =>
        HttpResponse.json(holder.session)
      ),
      http.get(`/api/v1/sessions/${holder.session.sessionId}/questions/current`, () =>
        HttpResponse.json(holder.question)
      )
    );
    const { client, fake } = fakeRealtimeClient();

    renderApp(`/sessions/${holder.session.sessionId}/play`, { realtimeClient: client });
    await screen.findByText(/read the question/i);
    act(() => fake.simulateConnect());

    // No host command exists to skip ahead — only the server's own
    // question.started broadcast moves the client past the reading period.
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

    expect(await screen.findByText("True")).toBeInTheDocument();
    expect(screen.queryByText(/read the question/i)).not.toBeInTheDocument();
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
      }),
      http.get(`/api/v1/sessions/${holder.session.sessionId}/leaderboard/top-five`, () =>
        HttpResponse.json(
          topFiveTransitionResponse({
            sessionId: holder.session.sessionId,
            questionId: holder.question.questionId
          })
        )
      )
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
    // The projected Top 5 renders the server's rows verbatim.
    expect(await screen.findByText("Fran")).toBeInTheDocument();
    expect(screen.getByText("Ann")).toBeInTheDocument();
    // Question 1 of 2 → the next advance is a plain next question, once
    // the board has settled.
    await user.click(screen.getByRole("button", { name: /skip animation/i }));
    expect(await screen.findByRole("button", { name: /next question/i })).toBeEnabled();
    expect(calls).toEqual(["reveal", "leaderboard"]);
  });

  it("finishes straight from the last question's reveal, with no leaderboard in between", async () => {
    signIn();
    const base = currentQuestionResponse({ questionNumber: 2, totalQuestions: 2 });
    const question = revealedQuestionResponse(base);
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "ANSWER_REVEALED"
    });
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, question);
    const calls: string[] = [];
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}/leaderboard/top-five`, () => {
        calls.push("top-five");
        return HttpResponse.json(
          apiError("session.top-five.not-available", "Not available for this question"),
          { status: 409 }
        );
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

    // The last question's reveal offers the ceremony, not a leaderboard.
    const finish = await screen.findByRole("button", { name: /finish quiz/i });
    expect(screen.queryByRole("button", { name: /show leaderboard/i })).not.toBeInTheDocument();

    await user.click(finish);

    // Exactly one command, and it is the advance — the leaderboard step
    // was never entered, so nothing could have flashed the finishing
    // order onto the projector before the podium.
    expect(calls).toEqual(["advance"]);
    // And the Top 5 projection was never even requested for it.
    expect(calls).not.toContain("top-five");
  });

  it("never mounts a leaderboard if the last question somehow lands on the leaderboard phase", async () => {
    signIn();
    // A stale tab or a session that entered LEADERBOARD before this rule
    // existed: the host screen must still route to the ceremony, never
    // render standings.
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
    let topFiveRequested = false;
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}/leaderboard/top-five`, () => {
        topFiveRequested = true;
        return HttpResponse.json(topFiveTransitionResponse({ sessionId: session.sessionId }));
      }),
      http.get(`/api/v1/sessions/${session.sessionId}/results`, () =>
        HttpResponse.json(
          sessionResultsResponse({ sessionId: session.sessionId, currentPhase: "LEADERBOARD" })
        )
      )
    );

    renderApp(`/sessions/${session.sessionId}/play`);

    expect(await screen.findByText(/that was the last question/i)).toBeInTheDocument();
    // No standings of any kind: not the animated board, not the table.
    expect(screen.queryByText("Fran")).not.toBeInTheDocument();
    expect(screen.queryByText("Ann")).not.toBeInTheDocument();
    expect(topFiveRequested).toBe(false);
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

  it("shows per-option counts and percentages once revealed, matching for English and Hindi", async () => {
    signIn();
    const base = currentQuestionResponse();
    const bilingual: CurrentQuestionResponse = {
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
    const revealed = revealedQuestionResponse(bilingual);
    const session = sessionSummary({
      sessionId: revealed.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: revealed.questionId,
      currentPhase: "ANSWER_REVEALED"
    });
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, revealed);
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}/results`, () =>
        HttpResponse.json(
          sessionResultsResponse({ sessionId: session.sessionId, currentPhase: "ANSWER_REVEALED" })
        )
      ),
      http.get(`/api/v1/sessions/${session.sessionId}/answer-distribution`, () =>
        HttpResponse.json(
          answerDistributionResponse({
            sessionId: session.sessionId,
            questionId: revealed.questionId,
            answeredCount: 18,
            eligibleParticipantCount: 20,
            noAnswerCount: 2,
            options: [
              { optionId: base.options![0].optionId!, count: 12, percentage: 60 },
              { optionId: base.options![1].optionId!, count: 6, percentage: 30 }
            ]
          })
        )
      )
    );

    renderApp(`/sessions/${session.sessionId}/play`);

    expect(await screen.findByText("सही")).toBeInTheDocument();
    // One count per option row, shared by both language lines — English and
    // Hindi can never disagree since it is the same rendered node.
    expect(await screen.findByText("12 · 60%")).toBeInTheDocument();
    expect(screen.getByText("6 · 30%")).toBeInTheDocument();
    expect(screen.getByText("No answer: 2")).toBeInTheDocument();
  });

  it("shows nothing before the reveal and hides the host-only distribution from participants", async () => {
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
    // No override: the default handler answers session.distribution.not-available.

    renderApp(`/sessions/${session.sessionId}/play`);

    expect(await screen.findByText(question.localizations![0].prompt!)).toBeInTheDocument();
    expect(screen.queryByText(/·/)).not.toBeInTheDocument();
    expect(screen.queryByText(/no answer/i)).not.toBeInTheDocument();
  });

  it("shows the projector-scale timer in Presentation Mode, and the compact one otherwise", async () => {
    const originalRequestFullscreen = HTMLElement.prototype.requestFullscreen;
    HTMLElement.prototype.requestFullscreen = () => Promise.reject(new Error("denied"));
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
    const user = userEvent.setup();

    renderApp(`/sessions/${session.sessionId}/play`);
    await screen.findByText(question.localizations![0].prompt!);
    expect(screen.queryByText(/time left/i)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /enter presentation mode/i }));

    expect(await screen.findByText(/time left/i)).toBeInTheDocument();
    expect(screen.getByRole("timer")).toBeInTheDocument();
    HTMLElement.prototype.requestFullscreen = originalRequestFullscreen;
  });

  it("drops the progress bar in Presentation Mode but keeps it otherwise", async () => {
    const originalRequestFullscreen = HTMLElement.prototype.requestFullscreen;
    HTMLElement.prototype.requestFullscreen = () => Promise.reject(new Error("denied"));
    usePresentationStore.setState({ active: false });
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
    const user = userEvent.setup();

    renderApp(`/sessions/${session.sessionId}/play`);
    await screen.findByText(question.localizations![0].prompt!);
    // Non-essential in Presentation Mode's reduction order, but present
    // in the normal layout — unchanged.
    expect(screen.getByRole("progressbar")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /enter presentation mode/i }));

    await screen.findByText(/time left/i);
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    HTMLElement.prototype.requestFullscreen = originalRequestFullscreen;
  });

  it("shows Answered and Time left together in one compact row in Presentation Mode", async () => {
    const originalRequestFullscreen = HTMLElement.prototype.requestFullscreen;
    HTMLElement.prototype.requestFullscreen = () => Promise.reject(new Error("denied"));
    usePresentationStore.setState({ active: false });
    signIn();
    const question = currentQuestionResponse();
    const session = sessionSummary({
      sessionId: question.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: question.questionId,
      currentPhase: "QUESTION_OPEN",
      participantCount: 10
    });
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, question);
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}/answer-progress`, () =>
        HttpResponse.json({
          sessionId: session.sessionId,
          questionId: question.questionId,
          answeredCount: 7,
          eligibleCount: 10
        })
      )
    );
    const user = userEvent.setup();

    renderApp(`/sessions/${session.sessionId}/play`);
    await screen.findByText(question.localizations![0].prompt!);

    await user.click(screen.getByRole("button", { name: /enter presentation mode/i }));

    const answered = await screen.findByText("7 / 10");
    const timeLeft = screen.getByRole("timer");
    // Same box primitive (PresentationMetric), same visual weight — and
    // sharing an immediate parent confirms they render as one compact row,
    // not a separate giant block below the header.
    expect(answered.closest('[role="status"]')?.parentElement).toBe(timeLeft.parentElement);
    const timeLeftValue = timeLeft.querySelector("span.tabular-nums");
    expect(answered.getAttribute("style")).toBe(timeLeftValue?.getAttribute("style"));
    HTMLElement.prototype.requestFullscreen = originalRequestFullscreen;
  });

  it("shows an Answered placeholder during the reading period, not a disappearing row, then real counts once open", async () => {
    const originalRequestFullscreen = HTMLElement.prototype.requestFullscreen;
    HTMLElement.prototype.requestFullscreen = () => Promise.reject(new Error("denied"));
    usePresentationStore.setState({ active: false });
    signIn();
    const base = currentQuestionResponse();
    const preview = previewQuestionResponse(base);
    const session = sessionSummary({
      sessionId: base.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: base.questionId,
      currentPhase: "QUESTION_PREVIEW"
    });
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, preview);
    const user = userEvent.setup();

    renderApp(`/sessions/${session.sessionId}/play`);
    await screen.findByText(preview.localizations![0].prompt!);

    await user.click(screen.getByRole("button", { name: /enter presentation mode/i }));

    // The Answered box is present with a placeholder — never absent — so
    // the row's width/height stays stable across the preview→open jump.
    const answeredDuringPreview = await screen.findByText("–");
    expect(answeredDuringPreview).toBeInTheDocument();
    expect(screen.getByRole("timer")).toBeInTheDocument();
    HTMLElement.prototype.requestFullscreen = originalRequestFullscreen;
  });

  it("keeps per-option reveal counts fitting inline in Presentation Mode", async () => {
    const originalRequestFullscreen = HTMLElement.prototype.requestFullscreen;
    HTMLElement.prototype.requestFullscreen = () => Promise.reject(new Error("denied"));
    usePresentationStore.setState({ active: false });
    signIn();
    const base = currentQuestionResponse();
    const revealed = revealedQuestionResponse(base);
    const session = sessionSummary({
      sessionId: base.sessionId,
      state: "IN_PROGRESS",
      currentQuestionId: base.questionId,
      currentPhase: "ANSWER_REVEALED"
    });
    serveQuiz(session.publishedQuizVersionId!);
    serveGameplay(session, revealed);
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}/results`, () =>
        HttpResponse.json(
          sessionResultsResponse({ sessionId: session.sessionId, currentPhase: "ANSWER_REVEALED" })
        )
      ),
      http.get(`/api/v1/sessions/${session.sessionId}/answer-distribution`, () =>
        HttpResponse.json(
          answerDistributionResponse({
            sessionId: session.sessionId,
            questionId: revealed.questionId,
            answeredCount: 10,
            eligibleParticipantCount: 10,
            options: [{ optionId: base.options![0].optionId!, count: 7, percentage: 70 }]
          })
        )
      )
    );
    const user = userEvent.setup();

    renderApp(`/sessions/${session.sessionId}/play`);
    await screen.findByText(revealed.localizations![0].prompt!);

    await user.click(screen.getByRole("button", { name: /enter presentation mode/i }));

    expect(await screen.findByText("7 · 70%")).toBeInTheDocument();
    // No progress bar or Presentation Mode timer clutters the reveal view.
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    HTMLElement.prototype.requestFullscreen = originalRequestFullscreen;
  });

  it("reveals five places — fifth through first — before the podium and remaining standings", async () => {
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
            participantCount: 6,
            entries: [
              leaderboardEntry({ displayName: "Ann", score: 900, rank: 1 }),
              leaderboardEntry({ displayName: "Ben", score: 800, rank: 2 }),
              leaderboardEntry({ displayName: "Cara", score: 700, rank: 3 }),
              leaderboardEntry({ displayName: "Dan", score: 600, rank: 4 }),
              leaderboardEntry({ displayName: "Eve", score: 500, rank: 5 }),
              leaderboardEntry({ displayName: "Fay", score: 100, rank: 6 })
            ]
          })
        )
      )
    );

    renderApp(`/sessions/${session.sessionId}/play`);

    expect(await screen.findByText(/and the winners are/i)).toBeInTheDocument();
    expect(screen.queryByText("Eve")).not.toBeInTheDocument();

    // Fifth and fourth enter — labeled Runner-up — before third, second, first.
    expect(await screen.findByText("Eve", undefined, { timeout: 3_000 })).toBeInTheDocument();
    expect(screen.getAllByText("Runner-up").length).toBeGreaterThan(0);
    expect(screen.queryByText("Ann")).not.toBeInTheDocument();
    expect(await screen.findByText("Ann", undefined, { timeout: 7_000 })).toBeInTheDocument();
    expect(screen.getAllByText("Winner").length).toBeGreaterThan(0);

    expect(
      await screen.findByLabelText("Podium", undefined, { timeout: 4_000 })
    ).toBeInTheDocument();
    expect(screen.getByText("Remaining standings")).toBeInTheDocument();
    expect(screen.getByText("Fay")).toBeInTheDocument();
    sessionStorage.removeItem(`quizchef.podium-played.${session.sessionId}`);
  }, 20_000);

  it("releases final results to participants, idempotently, only after the host acts", async () => {
    signIn();
    const session = sessionSummary({ state: "FINISHED", finalResultsReleased: false });
    sessionStorage.setItem(`quizchef.podium-played.${session.sessionId}`, "played");
    serveQuiz(session.publishedQuizVersionId!);
    let releaseCalls = 0;
    server.use(
      http.get(`/api/v1/sessions/${session.sessionId}`, () => HttpResponse.json(session)),
      http.get(`/api/v1/sessions/${session.sessionId}/questions/current`, () =>
        HttpResponse.json(apiError("session.no-current-question", "No question is in play"), {
          status: 409
        })
      ),
      http.get(`/api/v1/sessions/${session.sessionId}/results`, () =>
        HttpResponse.json(
          sessionResultsResponse({
            sessionId: session.sessionId,
            state: "FINISHED",
            currentPhase: undefined
          })
        )
      ),
      http.post(`/api/v1/sessions/${session.sessionId}/results/release`, () => {
        releaseCalls += 1;
        session.finalResultsReleased = true;
        return HttpResponse.json(session);
      })
    );
    const user = userEvent.setup();

    renderApp(`/sessions/${session.sessionId}/play`);
    expect(await screen.findByLabelText("Podium")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /reveal results to participants/i })
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /reveal results to participants/i }));

    expect(await screen.findByText(/results released to participants/i)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /reveal results to participants/i })
    ).not.toBeInTheDocument();
    expect(releaseCalls).toBe(1);
    sessionStorage.removeItem(`quizchef.podium-played.${session.sessionId}`);
  });

  it("reveals a solo finisher without waiting for four more places", async () => {
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
            participantCount: 1,
            entries: [leaderboardEntry({ displayName: "Ann", score: 900, rank: 1 })]
          })
        )
      )
    );

    renderApp(`/sessions/${session.sessionId}/play`);

    expect(await screen.findByText(/and the winners are/i)).toBeInTheDocument();
    expect(await screen.findByText("Ann", undefined, { timeout: 4_000 })).toBeInTheDocument();
    const podium = await screen.findByLabelText("Podium", undefined, { timeout: 4_000 });
    expect(within(podium).getAllByRole("listitem")).toHaveLength(1);
    expect(screen.queryByText("Remaining standings")).not.toBeInTheDocument();
    sessionStorage.removeItem(`quizchef.podium-played.${session.sessionId}`);
  }, 15_000);

  it("renders tied scores at their own distinct backend ranks, never merged", async () => {
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
            participantCount: 3,
            entries: [
              // Ann and Ben are tied on score — the backend still assigns
              // them distinct, sequential ranks (LeaderboardService fully
              // tie-breaks); the ceremony must render exactly that order,
              // never re-detect or merge the tie itself.
              leaderboardEntry({ displayName: "Ann", score: 900, rank: 1 }),
              leaderboardEntry({ displayName: "Ben", score: 900, rank: 2 }),
              leaderboardEntry({ displayName: "Cara", score: 500, rank: 3 })
            ]
          })
        )
      )
    );

    renderApp(`/sessions/${session.sessionId}/play`);

    const podium = await screen.findByLabelText("Podium");
    // Both tied entries render, at their own backend-assigned places —
    // the equal score is not collapsed into one row or one label.
    expect(within(podium).getByText("1st")).toBeInTheDocument();
    expect(within(podium).getByText("2nd")).toBeInTheDocument();
    expect(within(podium).getAllByText("900")).toHaveLength(2);
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
