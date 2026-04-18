-- Audit Tables

CREATE TABLE IF NOT EXISTS audit_logs (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    correlation_id         UUID NOT NULL,
    source_ehr_code        VARCHAR(50),
    target_ehr_code        VARCHAR(50),
    resource_type          VARCHAR(50),
    operation              VARCHAR(50),
    status                 VARCHAR(20),
    error_message          TEXT,
    error_code             VARCHAR(50),
    duration_ms            BIGINT DEFAULT 0,
    ai_mapping_used        BOOLEAN DEFAULT FALSE,
    ai_confidence_score    DOUBLE PRECISION,
    mapping_template_id    VARCHAR(50),
    mapping_template_version VARCHAR(20),
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_audit_status CHECK (status IN (
        'SUCCESS','FAILED','PARTIAL_SUCCESS','PENDING','RETRYING','SKIPPED'
    ))
);

-- Partition by month for large-scale audit (production recommendation)
-- CREATE TABLE audit_logs_2025_04 PARTITION OF audit_logs
--     FOR VALUES FROM ('2025-04-01') TO ('2025-05-01');

-- Indexes for common query patterns
CREATE INDEX idx_audit_correlation_id ON audit_logs(correlation_id);
CREATE INDEX idx_audit_source_ehr ON audit_logs(source_ehr_code, created_at DESC);
CREATE INDEX idx_audit_target_ehr ON audit_logs(target_ehr_code, created_at DESC);
CREATE INDEX idx_audit_status ON audit_logs(status, created_at DESC);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at DESC);
