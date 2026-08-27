DROP INDEX IF EXISTS idx_etf_holding_sector_attempts;

CREATE INDEX IF NOT EXISTS idx_etf_holding_sector_attempts
  ON etf_holding (sector_fetch_attempts)
  WHERE sector IS NULL;
