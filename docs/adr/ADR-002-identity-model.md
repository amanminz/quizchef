# ADR-002

Decision: Separate Identity, User, and Participant into distinct domain concepts (Identity → User → Participant).
Status: Accepted
Reason: Authentication concerns (User: registration, profile, roles, email, password) and gameplay concerns (Participant: joining, display name, score, connection status, session, ranking, answers) evolve independently. A Participant may be backed by a Guest Identity or a Registered User, so tying participation directly to registered accounts would make guest play a special case everywhere.
Consequences: The Auth module issues Identities (guest or registered). The User module owns the registered Identity. The Session module owns Participants, which always reference an Identity. Guest play and registered play share one code path. Replaces the original User → Participant model.
