ALTER TABLE etf_holding ADD COLUMN sector_source VARCHAR(20);

COMMENT ON COLUMN etf_holding.sector_source IS 'Source of sector classification: LLM or LIGHTYEAR';

UPDATE etf_holding SET sector_source = 'LLM' WHERE classified_by_model IS NOT NULL;
UPDATE etf_holding SET sector_source = 'LIGHTYEAR' WHERE sector IS NOT NULL AND classified_by_model IS NULL;
