DROP INDEX IF EXISTS idx_price_snapshot_instrument_hour;
CREATE INDEX idx_price_snapshot_lookup ON price_snapshot (instrument_id, provider_name, snapshot_hour DESC);
