-- EHR Registration Tables

CREATE TABLE IF NOT EXISTS ehr_registrations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ehr_code        VARCHAR(50) UNIQUE NOT NULL,
    display_name    VARCHAR(200) NOT NULL,
    org_name        VARCHAR(200),
    contact_email   VARCHAR(200),
    base_url        VARCHAR(500) NOT NULL,
    auth_type       VARCHAR(30) NOT NULL,
    auth_config     JSONB NOT NULL DEFAULT '{}',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    api_key_hash    VARCHAR(255),
    fhir_version    VARCHAR(10) DEFAULT 'R4',
    description     TEXT,
    version         VARCHAR(50),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_auth_type CHECK (auth_type IN (
        'NONE', 'BEARER_TOKEN', 'API_KEY', 'OAUTH2_CLIENT_CREDENTIALS', 'BASIC_AUTH'
    )),
    CONSTRAINT chk_status CHECK (status IN (
        'PENDING_REVIEW', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED'
    ))
);

CREATE TABLE IF NOT EXISTS ehr_endpoints (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ehr_id           UUID NOT NULL REFERENCES ehr_registrations(id) ON DELETE CASCADE,
    operation_type   VARCHAR(50) NOT NULL,
    http_method      VARCHAR(10) NOT NULL,
    path_template    VARCHAR(500) NOT NULL,
    request_headers  JSONB DEFAULT '{}',
    payload_template JSONB,
    path_params      JSONB DEFAULT '[]',
    prerequisites    JSONB DEFAULT '[]',
    response_path    VARCHAR(200),
    timeout_ms       INT NOT NULL DEFAULT 30000,
    retry_count      INT NOT NULL DEFAULT 3,
    transform_mode   VARCHAR(20) NOT NULL DEFAULT 'SYNC',
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_http_method CHECK (http_method IN ('GET','POST','PUT','PATCH','DELETE')),
    CONSTRAINT chk_transform_mode CHECK (transform_mode IN ('SYNC','ASYNC')),
    UNIQUE(ehr_id, operation_type)
);

-- Indexes
CREATE INDEX idx_ehr_registrations_status ON ehr_registrations(status);
CREATE INDEX idx_ehr_registrations_api_key_hash ON ehr_registrations(api_key_hash);
CREATE INDEX idx_ehr_endpoints_ehr_id ON ehr_endpoints(ehr_id);
CREATE INDEX idx_ehr_endpoints_operation ON ehr_endpoints(ehr_id, operation_type) WHERE is_active = TRUE;

-- Updated_at trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_ehr_registrations_updated_at
    BEFORE UPDATE ON ehr_registrations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
