ALTER TABLE instrument
DROP CONSTRAINT IF EXISTS instrument_provider_check;

ALTER TABLE daily_price
DROP CONSTRAINT IF EXISTS daily_price_provider_check;

ALTER TABLE instrument
  ADD CONSTRAINT instrument_provider_check
    CHECK (provider_name IN ('ALPHA_VANTAGE', 'BINANCE', 'FT', 'LIGHTYEAR', 'TRADING212'));

ALTER TABLE daily_price
  ADD CONSTRAINT daily_price_provider_check
    CHECK (provider_name IN ('ALPHA_VANTAGE', 'BINANCE', 'FT', 'LIGHTYEAR', 'TRADING212'));
