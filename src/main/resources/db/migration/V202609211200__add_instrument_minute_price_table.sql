CREATE TABLE IF NOT EXISTS instrument_minute_price
(
    id            BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT                   NOT NULL REFERENCES instrument (id) ON DELETE CASCADE,
    captured_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    price         NUMERIC(20, 10)          NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version       BIGINT                   NOT NULL DEFAULT 0,
    UNIQUE (instrument_id, captured_at)
);

CREATE INDEX idx_instrument_minute_price_captured_at ON instrument_minute_price (captured_at);

INSERT INTO instrument_minute_price (instrument_id, captured_at, price)
SELECT s.instrument_id, s.snapshot_hour + INTERVAL '59 minutes', s.price
FROM price_snapshot s
JOIN instrument i ON i.id = s.instrument_id AND i.provider_name = s.provider_name
WHERE s.snapshot_hour >= now() - INTERVAL '9 days'
  AND s.snapshot_hour < date_trunc('hour', now())
ON CONFLICT DO NOTHING;
