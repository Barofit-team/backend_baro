ALTER TABLE exercise_set_results ADD COLUMN routine_set_id UUID;
CREATE UNIQUE INDEX report_session_routine_set_unique ON exercise_set_results (session_id, routine_set_id) WHERE routine_set_id IS NOT NULL;
