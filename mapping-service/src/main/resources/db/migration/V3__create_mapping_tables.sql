-- Mapping Templates and Schema Registry

CREATE TABLE IF NOT EXISTS mapping_templates (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_ehr_code  VARCHAR(50) NOT NULL,
    target_ehr_code  VARCHAR(50) NOT NULL,
    resource_type    VARCHAR(50) NOT NULL,
    version          VARCHAR(20) NOT NULL DEFAULT '1.0.0',
    field_mappings   JSONB NOT NULL DEFAULT '[]',
    ai_generated     BOOLEAN NOT NULL DEFAULT FALSE,
    status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by       VARCHAR(100),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_mt_status CHECK (status IN ('ACTIVE','DEPRECATED','DRAFT'))
);

CREATE TABLE IF NOT EXISTS schema_registry (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ehr_code        VARCHAR(50) NOT NULL,
    resource_type   VARCHAR(50) NOT NULL,
    schema_version  VARCHAR(20) NOT NULL DEFAULT '1.0.0',
    json_schema     JSONB NOT NULL,
    inferred        BOOLEAN NOT NULL DEFAULT FALSE,
    approved        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(ehr_code, resource_type, schema_version)
);

-- Indexes
CREATE INDEX idx_mapping_templates_lookup
    ON mapping_templates(source_ehr_code, target_ehr_code, resource_type, status);
CREATE INDEX idx_mapping_templates_created
    ON mapping_templates(created_at DESC);
CREATE INDEX idx_schema_registry_lookup
    ON schema_registry(ehr_code, resource_type);

-- Seed: ECW → OMNIONE Patient mapping template
INSERT INTO mapping_templates (
    source_ehr_code, target_ehr_code, resource_type, version, field_mappings,
    ai_generated, status, created_by
) VALUES (
    'ECW', 'OMNIONE', 'PATIENT', '1.0.0',
    '[
      {"sourceField": "patient.id", "targetField": "id", "transformFunction": null, "confidenceScore": 1.0},
      {"sourceField": "patient.firstName", "targetField": "name[0].given[0]", "transformFunction": null, "confidenceScore": 0.99},
      {"sourceField": "patient.lastName", "targetField": "name[0].family", "transformFunction": null, "confidenceScore": 0.99},
      {"sourceField": "patient.dateOfBirth", "targetField": "birthDate", "transformFunction": "DATE_FORMAT_ISO", "confidenceScore": 0.98},
      {"sourceField": "patient.gender", "targetField": "gender", "transformFunction": "TO_LOWERCASE", "confidenceScore": 0.97},
      {"sourceField": "patient.phone", "targetField": "telecom[0].value", "transformFunction": "PHONE_FORMAT", "confidenceScore": 0.95},
      {"sourceField": "patient.email", "targetField": "telecom[1].value", "transformFunction": null, "confidenceScore": 0.95},
      {"sourceField": "patient.address.street", "targetField": "address[0].line[0]", "transformFunction": null, "confidenceScore": 0.96},
      {"sourceField": "patient.address.city", "targetField": "address[0].city", "transformFunction": null, "confidenceScore": 0.97},
      {"sourceField": "patient.address.state", "targetField": "address[0].state", "transformFunction": null, "confidenceScore": 0.97},
      {"sourceField": "patient.address.zip", "targetField": "address[0].postalCode", "transformFunction": null, "confidenceScore": 0.97},
      {"sourceField": "patient.mrn", "targetField": "identifier[0].value", "transformFunction": null, "confidenceScore": 0.99}
    ]'::jsonb,
    FALSE, 'ACTIVE', 'system'
) ON CONFLICT DO NOTHING;
