ALTER TABLE etf_holding ADD COLUMN sector_fetch_attempts INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_etf_holding_sector_attempts ON etf_holding(sector_fetch_attempts) WHERE sector_source IS NULL OR sector_source <> 'LLM';
