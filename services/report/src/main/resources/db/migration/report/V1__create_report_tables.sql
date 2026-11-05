CREATE TABLE exercise_set_results (
    set_id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    user_id UUID NOT NULL,
    exercise VARCHAR(50) NOT NULL,
    schema_version VARCHAR(20) NOT NULL CHECK (schema_version = '2.0'),
    engine_version VARCHAR(100) NOT NULL,
    frame_timestamp_ms DOUBLE PRECISION CHECK (frame_timestamp_ms >= 0),
    rep_count INTEGER CHECK (rep_count >= 0),
    hold_seconds DOUBLE PRECISION CHECK (hold_seconds >= 0),
    observed_seconds DOUBLE PRECISION CHECK (observed_seconds >= 0),
    gap_seconds DOUBLE PRECISION CHECK (gap_seconds >= 0),
    raw_result JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_report_sets_user_session ON exercise_set_results (user_id, session_id);
CREATE TABLE posture_item_results (
    set_id UUID NOT NULL REFERENCES exercise_set_results(set_id) ON DELETE CASCADE,
    item_id VARCHAR(100) NOT NULL,
    aggregation_basis VARCHAR(20) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    compliant DOUBLE PRECISION NOT NULL CHECK (compliant >= 0),
    warning DOUBLE PRECISION NOT NULL CHECK (warning >= 0),
    unknown_count DOUBLE PRECISION NOT NULL CHECK (unknown_count >= 0),
    not_applicable DOUBLE PRECISION NOT NULL CHECK (not_applicable >= 0),
    evaluated DOUBLE PRECISION NOT NULL CHECK (evaluated >= 0),
    compliance_pct DOUBLE PRECISION CHECK (compliance_pct BETWEEN 0 AND 100),
    PRIMARY KEY (set_id, item_id),
    CHECK ((aggregation_basis = 'repetition' AND unit = 'repetitions')
        OR (aggregation_basis = 'time' AND unit = 'seconds'))
);

-- 기존 Analytics 데이터는 보존하며 독립 Report 스키마로 복사한다.
DO $$
BEGIN
    IF to_regclass('analytics.exercise_set_results') IS NOT NULL
       AND to_regclass('analytics.posture_item_results') IS NOT NULL THEN
        INSERT INTO report.exercise_set_results SELECT * FROM analytics.exercise_set_results
            ON CONFLICT (set_id) DO NOTHING;
        INSERT INTO report.posture_item_results SELECT * FROM analytics.posture_item_results
            ON CONFLICT (set_id, item_id) DO NOTHING;
    END IF;
END $$;
