ALTER TABLE etf_holding ADD COLUMN classified_by_model VARCHAR(100);

COMMENT ON COLUMN etf_holding.classified_by_model IS 'AI model used for sector classification';
