CREATE TABLE workout.analytics_completion_outbox (
    session_id UUID PRIMARY KEY REFERENCES workout.workout_session(id) ON DELETE CASCADE,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_analytics_completion_outbox_pending
    ON workout.analytics_completion_outbox (next_attempt_at)
    WHERE delivered_at IS NULL;
