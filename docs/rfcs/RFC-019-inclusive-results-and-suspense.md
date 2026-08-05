# RFC-019 Nobody Learns They Came Last

Status

Implemented

<!-- Draft | Proposed | Accepted | Implemented | Superseded by RFC-XXX
     Implemented — the whole scope shipped in one PR, the same precedent as
     RFC-010/011/013/014/015/016/017/018. See README.md for the lifecycle. -->

Authors

Aman Minz

Created

2026-08-04

Updated

2026-08-05

---

# Summary

Until now a participant's phone showed their exact rank after every single question, and their exact final rank once the host released results. This RFC removes both. During the quiz, a participant sees whether they were right, what the question awarded them, their running total, and a line of encouragement — and nothing about where they stand. At the end, the podium and the top half get their exact position; everyone else gets their score and the names of the two players they finished between, with no position of their own on the wire at all. The cutoff is one domain rule, `FinalPlacementPolicy`, applied to both the projector and the phones so they cannot disagree. The rank-context endpoint that served the old per-question neighbours is deleted outright. Scoring, ranking, tie-breaking, the question preview, answer timing, the animated Top 5 (RFC-018), and the release workflow (RFC-015) are all untouched.

---

# Motivation

Two things were wrong with showing everyone their rank after every question, and only one of them was about suspense.

The first is the smaller one: the host is running an animated Top 5 on a projector (RFC-018) precisely so the room can watch the order change together. If forty phones have already displayed the new order privately, the projector is reporting news rather than breaking it.

The second matters more. QuizChef is used at church events, with mixed ages and mixed familiarity with the material, and someone is always last. Under the old design that person was told so after question one, and again after question two, and so on for the whole evening — and then told again, precisely, at the end. There is no version of that which is encouraging. A quiz can perfectly well reward the people at the front without informing everyone else exactly how far back they are, and the change costs nothing that anyone was enjoying.

So: ranks disappear from participant devices during play entirely, and the final reveal splits. The front of the field is celebrated by name and number, on the projector and on their own phones. Everyone else is told their score, who they finished near, and thank you.

---

# Goals

- No participant device can read its own rank at any point during the quiz — enforced by the shape of the responses, not by which components are mounted.
- Every answer reveal gives the participant something real: correctness, the points that question awarded, their total, and a line of encouragement in their own language.
- The final reveal gives exact positions to the podium and the top half, and supportive relative context to everyone else.
- The projector shows exactly the same reveal group the phones are split by, from the same number.
- The host keeps a complete, unabridged administrative view.

# Non-Goals

- Any change to `ScoringService`, `ScoringPolicy`, or `LeaderboardService`'s ranking and tie-break algorithm — untouched, and this RFC adds no ranking of its own.
- Any change to the animated Top 5 (RFC-018), the question preview (RFC-016), answer timing, or the release workflow (RFC-015). The host's ceremony still runs 5th → 1st and results are still released by an explicit host action, never automatically.
- Runtime-generated encouragement. The catalogue is written, reviewed, and committed (see Alternatives).
- Question Versioning (PR #47B) — still deferred.
- Duplicate-name enforcement — explicitly not combined into this work.

---

# Proposed Design

## During the quiz: the rank isn't hidden, it isn't computed

`SessionResultsQueryService.personalResult` used to rank the whole roster and return the caller's row. It now reads the participant, their total, and what the current question awarded them — and never calls `LeaderboardService` at all. `ParticipantResultView` has no rank component; neither does the response.

The distinction between "not returned" and "not computed" is the point. A nulled field invites a future edit to populate it, a log line to print it, or a debug endpoint to expose it. There is no rank in scope in that method to leak, which is a property a reviewer can check by reading twenty lines rather than by auditing every caller. `SessionResultsQueryServiceTest` asserts both halves: the record has no `rank` component, and `verifyNoInteractions(leaderboardService)`.

`pointsEarned` arrives on the same projection, read from the stored answer. That replaces the old client-side score delta (a diff of two consecutive snapshots, which vanished on refresh) with the server's own number, so a participant who reloads mid-reveal still sees `+750`.

**The rank-context endpoint is deleted** — service, view, response, exception, route, hook, and component. It existed only to show per-question neighbours, which is exactly the feature being removed; leaving it behind returning 409s would be a disabled endpoint that still exists, and the codebase's own precedent (no placeholder services, no dead code) says to remove it. Nothing of it is carried forward: the per-question neighbours it existed to serve are the feature being removed.

## Encouragement: a written catalogue, not a generated one

`motivation.ts` holds English and Hindi lines for correct, incorrect, unanswered, and quiz-complete. Selection is deterministic from `sessionId + participantId + outcome`, with the question number **added** to the resulting index rather than mixed into the hash. That is a deliberately small trick with two payoffs: the same key always yields the same line, so a refresh mid-reveal restores the message the player was already reading with nothing persisted; and consecutive questions with the same outcome always land on adjacent entries, so a player never sees the same sentence twice in a row. A hash over the whole key would be deterministic too, and would cheerfully repeat itself on questions 4 and 5 — which reads like the app has stopped paying attention.

The file carries four house rules for anything added to it (no position, no comparison, no sarcasm or pity, look forward not back), and `motivation.test.ts` enforces the first of them across every language, outcome, and question number with a forbidden-word pattern. That test also documents the one interesting false positive: Hindi *आगे बढ़ते रहें* ("keep going") contains *आगे*, which a naive "ahead" check flags, and which is no more a claim about position than English "move on".

## The final split: one policy, two consumers

`FinalPlacementPolicy.exactRankRevealCount(ranked)` returns:

```text
revealCount = max( min(5, totalParticipants), ceil(totalParticipants / 2) )
```

— at least the five ceremonial places (or the whole room, if smaller), at least half the room once half exceeds five:

| Participants | Exact ranks revealed | Relative-only |
| ---: | ---: | ---: |
| 1 | 1 | 0 |
| 4 | 4 | 0 |
| 6 | 5 | 1 |
| 10 | 5 | 5 |
| 11 | 6 | 5 |
| 20 | 10 | 10 |

That table is pinned in three places that must agree: this RFC, the `FinalPlacementPolicy` javadoc, and `FinalPlacementPolicyTest`'s parameterised cases, which assert both columns.

It then expands the cutoff while the entry at the boundary shares a rank with the one before it, so a shared rank is never split.

That expansion is written against **ranks, never scores**. `LeaderboardService` breaks every tie (submission time, then join order) and currently emits dense, unique ranks, so the loop never actually expands anything today. It is there so the rule is correct rather than accidentally correct, and so no future caller has to remember the edge case — the same reasoning RFC-018 applied to withholding ranks below fifth.

Two consumers, one number:

- `SessionResultsView`/`Response` gains `exactRankRevealCount` alongside its complete `entries`. The host's read stays unabridged — this is the administrative view, and the host running the event is entitled to all of it — but the ceremony renders `entries.slice(3, exactRankRevealCount)` below the podium and stops.
- `ParticipantFinalPlacementQueryService` uses the same call to decide which shape each participant gets.

Because both read the same function over the same ranking, the projector and the phones cannot disagree about where the line falls — which would otherwise be a genuinely nasty bug to notice, since it only shows up as one person's screen contradicting the big one.

## The participant's finish: two shapes, and the absent one is absent

`GET /sessions/{id}/participants/{participantId}/final-placement` returns either:

- `EXACT_RANK` — `rank`, `score`, `label` (`WINNER` 1–3, `RUNNER_UP` 4–5, `FINALIST` 6+ inside the group).
- `RELATIVE_ONLY` — `score`, and `aheadOf`/`behind` carrying **display names only**.

The neighbour type has exactly one component. There is no field on it for a rank, a score, or a gap, so none can be sent, cached, logged, or rendered by a later mistake — the guarantee is structural, and `ParticipantFinalPlacementQueryServiceTest` asserts it by reflecting over the record's components rather than by checking a particular response body.

**There is no `tiedWith`.** An earlier revision carried one, firing only on an equal *rank* rather than an equal score — which, since `LeaderboardService` orders the field totally, meant it could never fire at all. Review rightly called that out: an unreachable field on a privacy-sensitive response is complexity without behaviour, and untested-in-anger code on exactly the path where that matters most. It is gone. Two players on the same score are still one ahead of the other, and the response says so, which is both true and what the ranking means. `describesEqualScoresByTheirCanonicalOrderAndNeverAsTied` pins that with twelve players on identical scores; the frontend has the matching case. If the ranking model ever gains genuinely shared ranks, the wording for them belongs on the server's shape first — and the RFC that introduces shared ranks is where it should be designed.

The label rule moved from the frontend's `finalResultLabel` (which computed it from a rank) to the server, since the participant who most needs a label is now the one who is not told their rank.

## The projector: pages, not a scroll bar

`RevealedStandingsPages` renders the reveal group below the podium in fixed ten-row pages with host-driven Previous/Next. Pages rather than a scroll container because nobody can scroll a projector — the host is at a lectern and the audience is thirty feet away — and fixed-size pages because a page that reflows as it fills is unreadable at that distance. The component renders exactly the rows it is handed and knows nothing about the cutoff; the cut is applied by the caller from the server's number, so there is no second opinion here to drift.

---

# Alternatives Considered

**Hiding the rank in the UI and leaving the API as it was** — rejected. That is the version where a cached response, a devtools panel, or a component added next month re-exposes it, and where "does a participant know their rank?" cannot be answered by reading the server. The whole point of removing rank from the projection is that the answer becomes structural.

**Keeping the rank-context endpoint, disabled** — rejected. A permanently-409ing endpoint is dead code with a URL. The repository's own precedent (no placeholder application services, RFC-004's "no dead code") is to delete it and let git remember.

**Generating encouragement with a model at runtime** — rejected, and not narrowly. These lines are the only feedback a participant now receives, they render in front of a congregation including children, and some of them land on the person who came last. That is a thing to write once and review, not to delegate to a generation call at 200ms notice with no opportunity to read the output first. It would also add a per-question API dependency to the most latency-sensitive screen in the product, for prose that four fixed sentences cover.

**Deriving the reveal cutoff on the frontend from `participantCount`** — rejected: two implementations of one rule, one of which is in a place that must never rank anything. The server sends the number.

**Sending the lower half their exact rank and asking the client not to render it** — rejected for the same reason RFC-018 withheld ranks below fifth: a rule that only exists in a component is a rule the next component breaks.

**Telling the lower half their percentile or a band ("top 60%")** — rejected. It is the same disclosure in softer words, and it is worse in a small room, where "top 60%" of eight people is trivially decodable into a position.

**Keeping `rank` on `personalResult` for the final screen only** — rejected: it would mean the exact field this RFC removes still exists, gated by a phase check, which is the arrangement that produced RFC-017's leak in the first place.

---

# Risks

- **One deployment combination is cosmetically broken and cannot be fixed from this side.** The backend and frontend are separate Railway services built from the same push and do not land together. New-frontend-against-old-backend degrades quietly (a missing `/final-placement` reads as `http.404` → the announcement-waiting screen via `isEndpointMissing`; a missing `pointsEarned` omits that line; a `rank` still on the wire is never read — all covered by tests). **Old-frontend-against-new-backend renders `rankOrdinal(rank ?? 0)` — a 6xl "0th"** — because that bundle is already shipped, and the only server-side remedy would be to keep sending the rank this release removes. An old *host* bundle would also render the full standings below the podium, predating the cutoff. Mitigation is operational and documented in the [deployment checklist](../operations/deployment-checklist.md): deploy when no session is live (already the standing rule during events), and confirm both services report the new build before starting one.
- **The pagination row count (10) and the `clamp()` sizes were reasoned about, not measured** — jsdom has no layout engine. A twenty-person room's second page is the first real test of it, and the manual checklist remains the acceptance test for the projector half, as in RFC-017 and RFC-018.
- **The cutoff is generous in small rooms**: at four participants everyone is inside the reveal group, so nobody gets the relative-only treatment and the fourth player does learn they came fourth. That follows from `min(5, total)` and is intended — a room of four has no anonymity to offer anyone — but it does mean the feature's benefit only appears from about six participants up.
- **The Hindi catalogue was written without a native reviewer in the loop.** The lines are simple and the forbidden-word test guards the one rule that matters most, but tone is not something a regex checks; this should be read by a Hindi speaker before the next event.

---

# Migration

None. No database schema changed: every projection here is computed per request from data already stored (`Participant.totalScore` and its answers), never persisted (ADR-006). The API changes are one new endpoint, one new response field on the host's results read, one removed field (`rank` on the participant's progress read), and one removed route (`rank-context`) — all deployed together with the only client that uses them.

---

# Acceptance Criteria

- [x] `personalResult` returns no rank at any phase, and does not call the ranking service at all — asserted structurally, not by response body.
- [x] The rank-context endpoint, service, view, response, exception, hook, and component are gone; the participant client calls neither it, nor the host's standings read, nor the host's Top 5, at any point in a game.
- [x] `pointsEarned` is the server's own number for the question in play, and survives a refresh mid-reveal.
- [x] The cutoff is correct at 1, 4, 6, 10, 11, and 20 participants, and expands rather than splitting a shared rank — with a separate test proving equal *scores* do not trigger that expansion.
- [x] Ranks 1–3 are `WINNER`, 4–5 `RUNNER_UP`, and the rest of the group `FINALIST`, assigned by the server.
- [x] A participant outside the reveal group receives no rank, no label, no neighbour rank, no neighbour score, and no gap — the neighbour record has exactly one component.
- [x] The last player is told only whom they finished behind; a room of one gets first place; equal scores are described by the ranking's own order and never as a tie, backend and frontend.
- [x] Nothing at all is readable before the host's release, from either participant endpoint.
- [x] The projector renders the podium plus the reveal group and stops — a sixth player in a room of six never appears on it.
- [x] Revealed standings paginate at a fixed size, with keyboard-operable controls and no scrolling.
- [x] Every catalogue line, in both languages and all four outcomes, is free of positional language; selection is deterministic and never repeats on consecutive questions.
- [x] RFC-015's ceremony and release tests, RFC-017's holds, and RFC-018's Top 5 all pass with only the changes their own contracts required.
- [x] A backend without `/final-placement` yields the announcement-waiting screen rather than an error, is not retried, and never produces a rank; an older backend's response shape (a `rank` present, `pointsEarned` absent) renders correctly and shows no position.

---

# Future Work

- A native-speaker review of the Hindi catalogue, and catalogues for the dormant languages (`kn`, `ta`, `te`, `ml`) before any event that needs them — they currently fall back to English, which is honest but not good.
- Revisit the cutoff after an event with a large room. `ceil(total / 2)` is a guess informed by nothing but taste; the interesting question is whether a fixed reveal group (say, the top ten) reads better on a projector than a proportional one.
- If `LeaderboardService` ever adopts genuinely shared ranks, that RFC should design the "finished alongside" wording, add it to the placement shape, and cover it with an integration test driven by a real tied ranking — rather than reviving the unreachable field this one removed.
- Question Versioning (PR #47B).
