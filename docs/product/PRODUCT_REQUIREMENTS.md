# QuizChef Product Requirements Document (PRD)

**Version:** 1.0

**Status:** Living Document

**Release:** v1.0 (BELC Edition)

**Owner:** Aman Minz

---

# 1. Product Vision

QuizChef is an open-source platform for creating, hosting, and playing engaging live quizzes.

QuizChef is inspired by Kahoot but designed to be:

- Self-hostable
- Open Source
- Extensible
- Mobile Friendly
- Organization Agnostic

The first deployment is for Bangalore Evangelical Lutheran Church (BELC) to conduct Bible quizzes.

---

# 2. Problem Statement

Many churches conduct Bible quizzes using paper or PowerPoint.

Problems include:

- Manual score calculation
- Slow progression
- Difficult participation
- No real-time leaderboard
- No reusable question bank
- No history

QuizChef solves these problems by providing a live interactive quiz platform.

---

# 3. Target Audience

### Initial

Churches

### Future

Schools

Colleges

Companies

Training Organizations

Communities

Families

---

# 4. Product Goals

Primary goals

- Easy to host quizzes
- Fun participant experience
- Fast setup
- Mobile friendly
- Open Source
- Production ready

Secondary goals

- Reusable quizzes
- Beautiful UI
- Extensible architecture

---

# 5. Out of Scope (V1)

Not included in Version 1

- Teams
- Organizations
- Tournament Mode
- AI Question Generation
- Self-paced Quizzes
- Quiz Marketplace
- Offline Support
- Mobile Apps
- Analytics Dashboard
- Notifications
- Email Verification
- Password Reset

---

# 6. Identity Model and User Roles

## Identity Model

Identity

↓

User

↓

Participant

Every visitor has an Identity: a Guest Identity or a Registered User.

A User is a registered account: authentication, registration, profile, roles, email, password.

A Participant is an Identity playing in a live session: display name, current score, connection status, session, ranking, answers.

A Participant may be backed by a Guest Identity or a Registered User.

## User Roles

Guest

- Join quiz
- Play quiz

Registered User

- Everything Guest can do
- View quiz history (future)

Quiz Master

- Create quizzes
- Edit quizzes
- Host sessions

Administrator

- Manage platform

---

# 7. User Stories

## Guest

As a guest

I want to join using a PIN

So that I can play immediately.

---

As a guest

I want to enter my display name

So others can identify me.

---

As a participant

I want to answer questions on my phone

So I can compete.

---

As a participant

I want a live leaderboard

So I know my ranking.

---

As a participant

I want to be reconnected automatically after losing my connection

So I keep my score and never have to rejoin manually.

---

As a Quiz Master

I want to create quizzes

So I can host Bible quizzes.

---

As a Quiz Master

I want to upload images

So questions become more engaging.

---

As a Quiz Master

I want explanations after each answer

So participants can learn.

---

# 8. Functional Requirements

## Landing Page

Display

- Upcoming Quizzes
- Upcoming Events
- Login
- Register
- Join Quiz

---

## Authentication

Register

Login

Guest Play

JWT Authentication

---

## Quiz Management

Create Quiz

Edit Quiz

Delete Quiz

Publish Quiz

Archive Quiz

Reuse Quiz

---

## Question Management

Question Text

Options

Correct Answer

Image

Audio

Video

Bible Reference

Explanation

Time Limit

---

## Live Session

Generate PIN

Lobby

Join

Leave

Reconnect (score and progress preserved)

Host Controls

Realtime Updates

---

## Gameplay

Display Question

Countdown Timer

Answer Submission

Lock Answers

Reveal Correct Answer

Show Explanation

Leaderboard

Next Question

Winner

Reconnection: "Welcome back! You've been reconnected to the quiz." — then restore the current question, remaining timer, previously submitted answer (if any), current score, and leaderboard position.

---

# 9. Non Functional Requirements

Responsive

Accessible

Fast

Reliable

Secure

Extensible

Open Source

## Session Recovery

Participants should automatically recover from temporary network interruptions, browser refreshes, and WebSocket disconnects without losing their score or quiz progress. Recovery should occur transparently whenever possible and should not require rejoining the quiz manually.

---

# 10. User Journey

Quiz Master

Login

↓

Create Quiz

↓

Publish

↓

Start Session

↓

Share PIN

↓

Host Quiz

↓

End Session

---

Player

Landing Page

↓

Enter PIN

↓

Guest or Login

↓

Display Name

↓

Lobby

↓

Wait

↓

Answer Questions

↓

Leaderboard

↓

Winner Screen

---

# 11. Quiz Lifecycle

Draft

↓

Published

↓

Hosted

↓

Completed

↓

Archived

---

# 12. Session Lifecycle

Created

↓

Lobby

↓

Running

↓

Reveal Answer

↓

Leaderboard

↓

Running

↓

Completed

## Participant Lifecycle

Created

↓

Connected

↓

Disconnected

↓

Reconnected

↓

Finished

Disconnecting never removes a participant or resets their score. A participant is a durable session entity; connections are ephemeral.

---

# 13. Scoring Rules

Correct answers receive points.

Faster answers receive more points.

Incorrect answers receive zero.

Score Formula

Base Score

+

Time Bonus

Difficulty multiplier reserved for future.

---

# 14. Supported Question Types

Version 1

- Single Choice
- True / False

Future

- Multiple Choice
- Ordering
- Fill in Blank
- Image Selection
- Matching

---

# 15. Media Support

Every question may contain

Text

Image

Audio

Video

Bible Reference

Explanation

---

# 16. Wireframes

Landing

------------------------------------

Logo

Upcoming Quiz

Upcoming Events

Join Quiz

Login

------------------------------------

Lobby

------------------------------------

Quiz Name

PIN

Participants

Waiting for Host

------------------------------------

Question

------------------------------------

Question

Image

Options

Timer

------------------------------------

Leaderboard

------------------------------------

Rank

Player

Score

------------------------------------

Winner

------------------------------------

Congratulations

Top 3

Final Scores

------------------------------------

---

# 17. Acceptance Criteria

Landing Page

✓ Displays upcoming quizzes

✓ Join Quiz button visible

Authentication

✓ Guest play supported

Quiz

✓ Quiz CRUD

Session

✓ PIN generation

✓ Join via PIN

✓ Reconnection restores score, answers, and progress

Gameplay

✓ Timer

✓ Live updates

✓ Leaderboard

Deployment

✓ Runs using Docker Compose

✓ Deployable

---

# 18. Success Metrics

Technical

- Build passes
- CI passes
- Zero critical bugs

Product

- Quiz hosted successfully
- 100+ simultaneous participants
- Average response latency below 300 ms

User Experience

- Join in under 30 seconds
- No page refresh required
- Mobile friendly

---

# 19. Release Checklist

Backend

- Authentication
- Quiz CRUD
- Session Engine
- WebSocket

Frontend

- Landing
- Lobby
- Gameplay
- Leaderboard

Infrastructure

- Docker
- GitHub Actions
- PostgreSQL
- MinIO

Documentation

- README
- Architecture
- Coding Standards
- AI Guidelines
- API

---

# 20. Product Roadmap

## Version 1

BELC Live Bible Quiz

## Version 1.1

Question Bank

Import / Export

QR Code Join

## Version 2

Organizations

Team Mode

Analytics

Notifications

## Version 3

AI Quiz Builder

Self-paced Quizzes

Tournament Mode

Mobile Apps

---

# 21. Guiding Principle

Version 1 should delight users, not impress engineers.

Every feature should answer one question:

"Does this make hosting and playing quizzes easier?"