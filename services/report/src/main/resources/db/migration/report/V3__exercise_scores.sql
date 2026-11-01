CREATE TABLE exercise_scores (
    session_id UUID NOT NULL,
    routine_exercise_id UUID NOT NULL,
    user_id UUID NOT NULL,
    exercise_id VARCHAR(50) NOT NULL,
    score DOUBLE PRECISION NOT NULL CHECK (score >= 0 AND score <= 100),
    model_version VARCHAR(100),
    completed_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (session_id, routine_exercise_id)
);
CREATE INDEX idx_exercise_scores_history ON exercise_scores (user_id, exercise_id, completed_at DESC);
