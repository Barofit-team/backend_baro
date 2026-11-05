ALTER TABLE workout.workout_session ADD COLUMN rest_duration_seconds INTEGER CHECK (rest_duration_seconds >= 0);
