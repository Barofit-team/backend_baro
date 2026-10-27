# BaroFit Report Service - Session Ownership Contract

## Status
PROPOSED - Integration contract not yet approved.

## Current implementation
- JWT authentication identifies the requesting user.
- Report reads are filtered by authenticated userId and sessionId.
- Existing setId cannot be reused by another user or session.
- New sessionId ownership is NOT independently verified on set creation.
- workout_record_views represents completed workouts, not necessarily active sessions.

## Required security invariant
A user may create a report set only for a workout session
that is authoritatively associated with that authenticated user.

The client must not be trusted to assert ownership through
a userId or sessionId field alone.

## Proposed integration contract

1. Workout session is created by an authenticated backend endpoint.
2. Backend persists sessionId, owner userId, status and creation time.
3. Report Service verifies session ownership before accepting a new set.
4. A different user's session must be rejected.
5. Unknown session IDs must be rejected.
6. Retry of an existing identical set remains idempotent.
7. Offline replay must use a previously authorized session.
8. Session lifecycle and expiration rules require agreement.

## Open decisions for Routine / App / Backend teams

- Which service owns workout-session creation?
- Which database or API is authoritative for ownership?
- When is a workout session created and completed?
- Can offline-created session IDs be accepted?
- How are offline uploads authenticated after reconnection?
- Can sets be uploaded after session completion?
- What retention and expiration rules apply?
- What happens when session ownership lookup is unavailable?

## Acceptance tests required after implementation

- Owner can save a set to an active session.
- Different user cannot save to that session.
- Unknown sessionId is rejected.
- Client-supplied userId cannot override JWT identity.
- Offline replay of an authorized session succeeds.
- Duplicate concurrent uploads create only one set.
- Invalid item data rolls back the transaction.

## Release blocker

Do not claim complete session-ownership authorization
until an authoritative backend session contract exists
and its acceptance tests pass.

## Existing verification

- HTTP tests: PASS
- JWT integration tests: PASS
- PostgreSQL integration tests: PASS
- Service unit tests: PASS
- Report concurrency and rollback tests: PASS