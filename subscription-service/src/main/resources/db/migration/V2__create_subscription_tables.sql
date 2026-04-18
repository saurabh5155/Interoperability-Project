-- Subscription and Routing Tables

CREATE TABLE IF NOT EXISTS subscription_plans (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_code        VARCHAR(50) UNIQUE NOT NULL,
    plan_name        VARCHAR(200) NOT NULL,
    max_targets      INT NOT NULL DEFAULT 1,
    allowed_resources JSONB NOT NULL DEFAULT '["PATIENT"]',
    rate_limit_rpm   INT NOT NULL DEFAULT 100,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    description      TEXT,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ehr_subscriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_ehr_id   UUID NOT NULL,
    source_ehr_code VARCHAR(50) NOT NULL,
    plan_id         UUID NOT NULL REFERENCES subscription_plans(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    starts_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMP WITH TIME ZONE,
    created_by      VARCHAR(100),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_sub_status CHECK (status IN ('ACTIVE','SUSPENDED','EXPIRED','CANCELLED'))
);

CREATE TABLE IF NOT EXISTS routing_rules (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id  UUID NOT NULL REFERENCES ehr_subscriptions(id),
    source_ehr_id    UUID NOT NULL,
    source_ehr_code  VARCHAR(50) NOT NULL,
    target_ehr_id    UUID NOT NULL,
    target_ehr_code  VARCHAR(50) NOT NULL,
    resource_type    VARCHAR(50) NOT NULL,
    target_operation VARCHAR(50) NOT NULL,
    transform_mode   VARCHAR(20) NOT NULL DEFAULT 'SYNC',
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    priority         INT NOT NULL DEFAULT 0,
    created_by       VARCHAR(100),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_rr_transform_mode CHECK (transform_mode IN ('SYNC','ASYNC'))
);

-- Seed default plans
INSERT INTO subscription_plans (plan_code, plan_name, max_targets, allowed_resources, rate_limit_rpm, description)
VALUES
    ('BASIC', 'Basic Plan', 1, '["PATIENT"]', 100, 'Single target EHR, Patient data only'),
    ('STANDARD', 'Standard Plan', 3, '["PATIENT","ENCOUNTER","CONDITION"]', 500, 'Up to 3 targets, core resources'),
    ('ENTERPRISE', 'Enterprise Plan', 20, '["PATIENT","ENCOUNTER","CONDITION","OBSERVATION","MEDICATION_REQUEST","ALLERGY_INTOLERANCE","PROCEDURE"]', 5000, 'Unlimited targets, all resource types')
ON CONFLICT (plan_code) DO NOTHING;

-- Indexes
CREATE INDEX idx_ehr_subscriptions_source_ehr ON ehr_subscriptions(source_ehr_code, status);
CREATE INDEX idx_routing_rules_source ON routing_rules(source_ehr_code, resource_type) WHERE is_active = TRUE;
CREATE INDEX idx_routing_rules_target ON routing_rules(target_ehr_code);
