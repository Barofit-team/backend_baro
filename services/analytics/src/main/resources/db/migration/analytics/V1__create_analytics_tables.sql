CREATE TABLE IF NOT EXISTS workout_record_views (
    session_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    routine_id UUID,
    completed_at TIMESTAMPTZ NOT NULL,
    duration_seconds INTEGER NOT NULL
        CHECK (duration_seconds >= 0),
    total_reps INTEGER NOT NULL
        CHECK (total_reps >= 0)
);

CREATE INDEX IF NOT EXISTS idx_workout_records_user_date
ON workout_record_views (user_id, completed_at DESC);
CREATE TABLE IF NOT EXISTS daily_statistics (
    user_id UUID NOT NULL,
    workout_date DATE NOT NULL,
    session_count INTEGER NOT NULL DEFAULT 0
        CHECK (session_count >= 0),
    total_duration_seconds INTEGER NOT NULL DEFAULT 0
        CHECK (total_duration_seconds >= 0),
    total_reps INTEGER NOT NULL DEFAULT 0
        CHECK (total_reps >= 0),
    PRIMARY KEY (user_id, workout_date)
);

CREATE TABLE IF NOT EXISTS calendar_entries (
    user_id UUID NOT NULL,
    workout_date DATE NOT NULL,
    session_count INTEGER NOT NULL DEFAULT 0
        CHECK (session_count >= 0),
    total_duration_seconds INTEGER NOT NULL DEFAULT 0
        CHECK (total_duration_seconds >= 0),
    PRIMARY KEY (user_id, workout_date)
);

CREATE INDEX IF NOT EXISTS idx_calendar_user_date
ON calendar_entries (user_id, workout_date);
