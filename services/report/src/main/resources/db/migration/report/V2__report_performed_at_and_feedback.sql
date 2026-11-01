ALTER TABLE exercise_set_results ADD COLUMN performed_at TIMESTAMPTZ;
CREATE INDEX idx_report_sets_user_performed ON exercise_set_results (user_id, performed_at);

CREATE TABLE workout_feedback (
    session_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    difficulty VARCHAR(20) NOT NULL CHECK (difficulty IN ('EASY','MODERATE','HARD')),
    discomfort_areas JSONB NOT NULL DEFAULT '[]'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_workout_feedback_user ON workout_feedback(user_id, session_id);
