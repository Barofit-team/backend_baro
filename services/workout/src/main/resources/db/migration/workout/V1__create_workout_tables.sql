CREATE TABLE workout.workout_session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    routine_id UUID NOT NULL,
    routine_snapshot JSONB NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','PAUSED','COMPLETED','ABANDONED')),
    revision BIGINT NOT NULL CHECK (revision >= 0),
    payload_hash VARCHAR(64) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    active_duration_seconds INTEGER NOT NULL DEFAULT 0 CHECK (active_duration_seconds >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((status IN ('COMPLETED','ABANDONED')) = (ended_at IS NOT NULL))
);
CREATE INDEX ix_workout_session_user_started ON workout.workout_session(user_id, started_at DESC);

CREATE TABLE workout.workout_set_result (
    session_id UUID NOT NULL REFERENCES workout.workout_session(id) ON DELETE CASCADE,
    routine_set_id UUID NOT NULL,
    actual_reps INTEGER CHECK (actual_reps >= 0),
    actual_duration_seconds INTEGER CHECK (actual_duration_seconds >= 0),
    feedback_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    feedback_text VARCHAR(1000),
    model_version VARCHAR(100),
    completed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (session_id, routine_set_id)
);
