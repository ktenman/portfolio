CREATE TABLE etf_holding_industry_backup (
  holding_uuid UUID PRIMARY KEY,
  industry VARCHAR(150) NOT NULL,
  industry_classified_by_model VARCHAR(100) NOT NULL,
  holding_version BIGINT NOT NULL,
  holding_updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  backed_up_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO etf_holding_industry_backup
  (holding_uuid, industry, industry_classified_by_model, holding_version, holding_updated_at)
SELECT uuid, industry, industry_classified_by_model, version, updated_at
FROM etf_holding
WHERE industry IS NOT NULL AND industry_classified_by_model IS NOT NULL;

ALTER TABLE etf_holding
  ADD COLUMN industry_source VARCHAR(20),
  ADD COLUMN industry_effective_date DATE;

UPDATE etf_holding
SET industry_source = CASE WHEN industry_classified_by_model IS NOT NULL THEN 'LLM' ELSE 'UNKNOWN' END
WHERE industry IS NOT NULL;
