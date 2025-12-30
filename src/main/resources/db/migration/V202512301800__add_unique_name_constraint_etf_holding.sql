CREATE UNIQUE INDEX IF NOT EXISTS idx_etf_holding_name_lower ON etf_holding (LOWER(name));
