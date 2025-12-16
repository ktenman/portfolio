ALTER TABLE instrument
DROP CONSTRAINT IF EXISTS instrument_provider_check;

ALTER TABLE daily_price
DROP CONSTRAINT IF EXISTS daily_price_provider_check;

ALTER TABLE instrument
  ADD CONSTRAINT instrument_provider_check
    CHECK (provider_name IN ('BINANCE', 'FT', 'LIGHTYEAR', 'MANUAL', 'TRADING212'));

ALTER TABLE daily_price
  ADD CONSTRAINT daily_price_provider_check
    CHECK (provider_name IN ('BINANCE', 'FT', 'LIGHTYEAR', 'MANUAL', 'TRADING212'));

INSERT INTO instrument (
    symbol,
    name,
    instrument_category,
    base_currency,
    provider_name,
    current_price,
    created_at,
    updated_at,
    version
) VALUES (
    'EUR',
    'Euro Cash',
    'CASH',
    'EUR',
    'MANUAL',
    1.0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);

INSERT INTO portfolio_transaction (
    instrument_id,
    transaction_type,
    quantity,
    price,
    transaction_date,
    platform,
    commission,
    currency,
    realized_profit,
    unrealized_profit,
    remaining_quantity
) VALUES (
    (SELECT id FROM instrument WHERE symbol = 'EUR'),
    'BUY',
    13719.16,
    1.0,
    CURRENT_DATE,
    'SWEDBANK',
    0,
    'EUR',
    0,
    0,
    13719.16
);

INSERT INTO daily_price (
    instrument_id,
    entry_date,
    provider_name,
    close_price,
    created_at,
    updated_at
) VALUES (
    (SELECT id FROM instrument WHERE symbol = 'EUR'),
    CURRENT_DATE,
    'MANUAL',
    1.0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
