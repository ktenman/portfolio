ALTER TABLE etf_holding
  ADD COLUMN industry VARCHAR(150),
  ADD COLUMN industry_classified_by_model VARCHAR(100),
  ADD COLUMN industry_fetch_attempts INTEGER NOT NULL DEFAULT 0;
