ALTER TABLE instrument
DROP CONSTRAINT IF EXISTS instrument_provider_check;

ALTER TABLE instrument
  ADD CONSTRAINT instrument_provider_check
  CHECK (provider_name IN ('BINANCE', 'FT', 'LIGHTYEAR', 'MANUAL', 'SYNTHETIC', 'TRADING212', 'TULEVA'));

ALTER TABLE daily_price
DROP CONSTRAINT IF EXISTS daily_price_provider_check;

ALTER TABLE daily_price
  ADD CONSTRAINT daily_price_provider_check
  CHECK (provider_name IN ('BINANCE', 'FT', 'LIGHTYEAR', 'MANUAL', 'TRADING212', 'TULEVA'));

INSERT INTO instrument (
    symbol,
    name,
    instrument_category,
    base_currency,
    fund_currency,
    provider_name,
    current_price,
    ter,
    created_at,
    updated_at,
    version
) VALUES (
    'EE3600001707',
    'Tuleva III Samba Pensionifond',
    'ETF',
    'EUR',
    'EUR',
    'TULEVA',
    0,
    0.28,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);
