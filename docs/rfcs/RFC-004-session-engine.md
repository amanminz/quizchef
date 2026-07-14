## RFC-004

Session Engine

Defines:

* PIN generation
* Lobby
* Join
* Participant model (backed by a Guest Identity or a Registered User)
* Participant lifecycle (Created → Connected → Disconnected → Reconnected → Finished)
* Participant token (secure random token returned on join, stored client-side, used for guest reconnection)
* Reconnection and state synchronization (SessionRecoveryService: restore score, answers, current question, remaining time, leaderboard; rebind connection; notify host)
* Single active connection policy (joining from a new device invalidates the previous connection)
* Host controls
* State machine
