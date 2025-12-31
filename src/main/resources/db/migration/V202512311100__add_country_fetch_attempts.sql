ALTER TABLE etf_holding ADD COLUMN country_fetch_attempts INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_etf_holding_country_attempts ON etf_holding(country_fetch_attempts) WHERE country_code IS NULL;
