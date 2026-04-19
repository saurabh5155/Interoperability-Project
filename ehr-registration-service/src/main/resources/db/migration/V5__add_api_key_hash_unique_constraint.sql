-- Add UNIQUE constraint on api_key_hash to prevent two registrations
-- from sharing the same hash, which would cause authentication ambiguity.
-- Existing rows with NULL api_key_hash are not affected (NULLs are not
-- considered equal under SQL uniqueness rules).

ALTER TABLE ehr_registrations
    ADD CONSTRAINT uq_ehr_api_key_hash UNIQUE (api_key_hash);
