ALTER TABLE etf_holding ADD COLUMN canonical_holding_id BIGINT REFERENCES etf_holding (id) ON DELETE SET NULL;

CREATE INDEX idx_etf_holding_canonical ON etf_holding (canonical_holding_id);

COMMENT ON COLUMN etf_holding.canonical_holding_id IS 'Self-FK pointing to canonical EtfHolding row when this row is a duplicate. NULL means this row is canonical.';
