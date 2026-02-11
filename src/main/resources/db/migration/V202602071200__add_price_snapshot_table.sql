CREATE TABLE IF NOT EXISTS price_snapshot
(
    id              BIGSERIAL PRIMARY KEY,
    instrument_id   BIGINT                   NOT NULL REFERENCES instrument (id),
    provider_name   VARCHAR(50)              NOT NULL,
    snapshot_hour   TIMESTAMP WITH TIME ZONE NOT NULL,
    price           NUMERIC(20, 10)          NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT                   NOT NULL DEFAULT 0,
    UNIQUE (instrument_id, provider_name, snapshot_hour)
);

CREATE INDEX idx_price_snapshot_instrument_hour ON price_snapshot (instrument_id, snapshot_hour DESC);
CREATE INDEX idx_price_snapshot_cleanup ON price_snapshot (snapshot_hour);
