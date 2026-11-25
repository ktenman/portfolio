DROP INDEX IF EXISTS idx_etf_position_snapshot;

CREATE INDEX idx_etf_position_snapshot ON etf_position (etf_instrument_id, snapshot_date) INCLUDE (holding_id, weight_percentage);
