ALTER TABLE etf_holding ADD COLUMN uuid UUID;

UPDATE etf_holding SET uuid = gen_random_uuid() WHERE uuid IS NULL;

ALTER TABLE etf_holding ALTER COLUMN uuid SET NOT NULL;

ALTER TABLE etf_holding ADD CONSTRAINT etf_holding_uuid_unique UNIQUE (uuid);

CREATE INDEX idx_etf_holding_uuid ON etf_holding (uuid);
