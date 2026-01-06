CREATE INDEX IF NOT EXISTS idx_etf_position_holding ON etf_position(holding_id);

CREATE INDEX IF NOT EXISTS idx_etf_holding_sector_null ON etf_holding(id) WHERE sector IS NULL;

CREATE INDEX IF NOT EXISTS idx_etf_holding_logo_null ON etf_holding(id) WHERE logo_source IS NULL;
