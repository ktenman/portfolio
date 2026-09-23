CREATE TABLE etf_holding_country_sector_backup (
  holding_uuid UUID PRIMARY KEY,
  country_code VARCHAR(2),
  country_name TEXT,
  country_classified_by_model VARCHAR(100),
  sector VARCHAR(150),
  sector_source VARCHAR(20),
  classified_by_model VARCHAR(100),
  holding_version BIGINT NOT NULL,
  holding_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  backed_up_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO etf_holding_country_sector_backup (
  holding_uuid, country_code, country_name, country_classified_by_model,
  sector, sector_source, classified_by_model, holding_version, holding_updated_at
)
SELECT uuid, country_code, country_name, country_classified_by_model,
       sector, sector_source, classified_by_model, version, updated_at
FROM etf_holding;

ALTER TABLE etf_holding
  ADD COLUMN country_source VARCHAR(20),
  ADD COLUMN country_effective_date DATE;

UPDATE etf_holding
SET country_source = CASE
  WHEN country_classified_by_model IS NOT NULL THEN 'LLM'
  ELSE 'UNKNOWN'
END
WHERE country_code IS NOT NULL AND BTRIM(country_code) <> '';
