-- Add LIGHTYEAR to provider_name check constraint
ALTER TABLE instrument DROP CONSTRAINT IF EXISTS instrument_provider_check;
ALTER TABLE instrument ADD CONSTRAINT instrument_provider_check
  CHECK (provider_name IN ('ALPHA_VANTAGE', 'BINANCE', 'FT', 'LIGHTYEAR'));

-- Create etf_holding table
CREATE TABLE etf_holding
(
  id         BIGSERIAL PRIMARY KEY,
  ticker     VARCHAR(50),
  name       VARCHAR(255)                                   NOT NULL,
  sector     VARCHAR(150),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  version    BIGINT                       DEFAULT 0             NOT NULL,
  UNIQUE (name, ticker)
);

-- Create etf_positions table
CREATE TABLE etf_positions
(
  etf_instrument_id BIGINT                                         NOT NULL REFERENCES instrument (id) ON DELETE CASCADE,
  holding_id        BIGINT                                         NOT NULL REFERENCES etf_holding (id) ON DELETE CASCADE,
  snapshot_date     DATE                                           NOT NULL,
  weight_percentage NUMERIC(8, 4)                                  NOT NULL,
  position_rank     INTEGER,
  market_cap        VARCHAR(50),
  price             VARCHAR(50),
  day_change        VARCHAR(50),
  created_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (etf_instrument_id, holding_id, snapshot_date)
);

-- Create indexes
CREATE INDEX idx_etf_positions_snapshot ON etf_positions (etf_instrument_id, snapshot_date);
CREATE INDEX idx_etf_positions_weight ON etf_positions (weight_percentage DESC);
