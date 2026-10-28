ALTER TABLE instrument ADD COLUMN uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE instrument ADD CONSTRAINT instrument_uuid_unique UNIQUE (uuid);

ALTER TABLE instrument ADD COLUMN logo_source VARCHAR(20);
