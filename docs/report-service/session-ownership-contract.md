# BaroFit Report Service - Session Ownership Contract

## Status
IMPLEMENTED for report-set HTTP uploads. Workout Service owns sessions; the independent Report Service checks ownership by calling its authenticated session-detail API before each new or repeated set upload.

## Current implementation
- JWT authentication identifies the requesting user.
- Report reads are filtered by authenticated userId and sessionId.
- Existing setId cannot be reused by another user or session.
- New sessionId ownership is verified against Workout Service using the caller's Bearer token. Unknown or another user's session returns 404; Workout lookup failure returns 503.
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

## Agreed behavior

- Workout Service owns session creation and lifecycle. `PUT /api/v1/workout-sessions/{sessionId}` accepts a device-generated UUID and stores the authenticated owner.
- Report calls `GET /api/v1/workout-sessions/{sessionId}` with the same Bearer token. A 200 permits the report upload. A 404 rejects unknown or other-user sessions. A 401/403 rejects authentication. Other responses or network failures fail closed with 503.
- Offline workouts must sync to Workout Service before their report sets are uploaded. Reports may be uploaded for active, paused, completed, or abandoned sessions because historical offline results can arrive after completion.
- Existing identical uploads remain idempotent, but ownership is rechecked on each request.
- Session retention/expiration remains a future decision. Qualifying finished sessions are delivered to Analytics through the Workout outbox; posture-set results are uploaded separately to Report.

## Acceptance tests required after implementation

- Owner can save a set to an active session.
- Different user cannot save to that session.
- Unknown sessionId is rejected.
- Client-supplied userId cannot override JWT identity.
- Offline replay of an authorized session succeeds.
- Duplicate concurrent uploads create only one set.
- Invalid item data rolls back the transaction.

## Verification

The ownership client tests cover authenticated lookup and unknown sessions; the report HTTP test checks that a failed ownership lookup prevents saving a set. End-to-end testing with real JWTs and synchronized offline sessions is still required before release.

## Existing verification

- HTTP tests: PASS
- JWT integration tests: PASS
- PostgreSQL integration tests: PASS
- Service unit tests: PASS
- Report concurrency and rollback tests: PASS
