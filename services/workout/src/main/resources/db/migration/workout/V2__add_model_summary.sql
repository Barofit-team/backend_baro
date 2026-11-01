ALTER TABLE workout.workout_set_result
    ADD COLUMN error_counts JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN reference_model_id VARCHAR(128),
    ADD COLUMN tracking_available_pct DOUBLE PRECISION;
