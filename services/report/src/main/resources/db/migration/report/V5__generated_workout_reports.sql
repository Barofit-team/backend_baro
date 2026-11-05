CREATE TABLE generated_workout_reports (
    session_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    report_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX generated_workout_reports_user_idx ON generated_workout_reports(user_id, session_id);
