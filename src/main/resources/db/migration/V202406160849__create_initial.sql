-- Enable the pg_trgm extension for text search
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Table for financial instruments (with instrument_category)
CREATE TABLE instrument
(
  id                  BIGSERIAL PRIMARY KEY,
  symbol              VARCHAR(20)                                        NOT NULL UNIQUE,
  name                VARCHAR(255),
  instrument_category VARCHAR(50),
  base_currency       VARCHAR(3)                                         NOT NULL DEFAULT 'EUR',
  created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Table for portfolio transactions
CREATE TABLE portfolio_transaction
(
  id               BIGSERIAL PRIMARY KEY,
  instrument_id    BIGINT REFERENCES instrument (id),
  transaction_type VARCHAR(10)                                        NOT NULL CHECK (transaction_type IN ('BUY', 'SELL')),
  quantity         NUMERIC(20, 8)                                     NOT NULL,
  price            NUMERIC(20, 8)                                     NOT NULL,
  transaction_date DATE                                               NOT NULL,
  created_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Table for daily price tracking
CREATE TABLE daily_price
(
  id            BIGSERIAL PRIMARY KEY,
  instrument_id BIGINT REFERENCES instrument (id),
  entry_date    DATE                                               NOT NULL,
  provider_name VARCHAR(255)                                       NOT NULL,
  open_price    NUMERIC(20, 8),
  high_price    NUMERIC(20, 8),
  low_price     NUMERIC(20, 8),
  close_price   NUMERIC(20, 8)                                     NOT NULL,
  volume        BIGINT,
  created_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at    TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  UNIQUE (instrument_id, entry_date, provider_name)
);

-- Combined table for portfolio daily summary
CREATE TABLE portfolio_daily_summary
(
  id                 BIGSERIAL PRIMARY KEY,
  entry_date         DATE                                               NOT NULL UNIQUE,
  total_value        NUMERIC(20, 8)                                     NOT NULL,
  xirr_annual_return NUMERIC(10, 8)                                     NOT NULL, -- Percentage
  total_profit       NUMERIC(20, 8)                                     NOT NULL, -- In euros
  earnings_per_day   NUMERIC(20, 8)                                     NOT NULL, -- (XIRR * total_value) / 365.25
  created_at         TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at         TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Indexes for performance
CREATE INDEX idx_instrument_symbol_trgm ON instrument USING gin (symbol gin_trgm_ops);
CREATE INDEX idx_instrument_name_trgm ON instrument USING gin (name gin_trgm_ops);
CREATE INDEX idx_instrument_category ON instrument (instrument_category);
CREATE INDEX idx_portfolio_transaction_instrument_id ON portfolio_transaction (instrument_id);
CREATE INDEX idx_portfolio_transaction_date ON portfolio_transaction (transaction_date);
CREATE INDEX idx_daily_price_instrument_id_date_provider ON daily_price (instrument_id, entry_date, provider_name);
CREATE INDEX idx_portfolio_daily_summary_date ON portfolio_daily_summary (entry_date);
