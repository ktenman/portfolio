ALTER TABLE instrument ADD COLUMN provider_external_id VARCHAR(255);

COMMENT ON COLUMN instrument.provider_external_id IS 'External identifier used by the provider (e.g., Lightyear UUID)';
