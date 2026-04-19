CREATE TABLE admin_audit_logs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_user   VARCHAR(100)  NOT NULL,
    action       VARCHAR(100)  NOT NULL,
    resource     VARCHAR(100),
    resource_id  VARCHAR(200),
    description  TEXT,
    ip_address   VARCHAR(45),
    created_at   TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_admin_audit_user       ON admin_audit_logs(admin_user);
CREATE INDEX idx_admin_audit_action     ON admin_audit_logs(action);
CREATE INDEX idx_admin_audit_created_at ON admin_audit_logs(created_at DESC);
