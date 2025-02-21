ALTER TABLE instrument
DROP CONSTRAINT IF EXISTS check_provider_name;

ALTER TABLE daily_price
DROP CONSTRAINT IF EXISTS check_provider_name;

ALTER TABLE instrument
  ADD CONSTRAINT instrument_provider_check
    CHECK (provider_name IN ('ALPHA_VANTAGE', 'BINANCE', 'FT'));

ALTER TABLE daily_price
  ADD CONSTRAINT daily_price_provider_check
    CHECK (provider_name IN ('ALPHA_VANTAGE', 'BINANCE', 'FT'));

UPDATE instrument
SET provider_name = 'FT'
WHERE symbol = 'QDVE:GER:EUR';

