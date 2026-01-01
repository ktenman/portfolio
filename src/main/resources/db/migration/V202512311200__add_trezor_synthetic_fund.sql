ALTER TABLE instrument
DROP CONSTRAINT IF EXISTS instrument_provider_check;

ALTER TABLE instrument
  ADD CONSTRAINT instrument_provider_check
    CHECK (provider_name IN ('BINANCE', 'FT', 'LIGHTYEAR', 'MANUAL', 'SYNTHETIC', 'TRADING212'));

INSERT INTO instrument (symbol, name, instrument_category, base_currency, provider_name, created_at, updated_at, version)
VALUES ('TREZOR', 'Trezor Crypto Fund', 'ETF', 'EUR', 'SYNTHETIC', NOW(), NOW(), 0);

INSERT INTO etf_holding (name, ticker, sector, created_at, updated_at, version)
SELECT 'Bitcoin', 'BTCEUR', 'Cryptocurrency', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM etf_holding WHERE LOWER(name) = LOWER('Bitcoin'));

INSERT INTO etf_holding (name, ticker, sector, created_at, updated_at, version)
SELECT 'Binance Coin', 'BNBEUR', 'Cryptocurrency', NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM etf_holding WHERE LOWER(name) = LOWER('Binance Coin'));

UPDATE etf_holding SET ticker = 'BTCEUR', sector = COALESCE(sector, 'Cryptocurrency')
WHERE LOWER(name) = LOWER('Bitcoin') AND (ticker IS NULL OR ticker = 'BTCEUR');

UPDATE etf_holding SET ticker = 'BNBEUR', sector = COALESCE(sector, 'Cryptocurrency')
WHERE LOWER(name) = LOWER('Binance Coin') AND (ticker IS NULL OR ticker = 'BNBEUR');

INSERT INTO etf_position (etf_instrument_id, holding_id, snapshot_date, weight_percentage, created_at, updated_at, version)
SELECT
  (SELECT id FROM instrument WHERE symbol = 'TREZOR'),
  h.id,
  CURRENT_DATE,
  0,
  NOW(), NOW(), 0
FROM etf_holding h
WHERE LOWER(h.name) IN (LOWER('Bitcoin'), LOWER('Binance Coin'))
  AND NOT EXISTS (
    SELECT 1 FROM etf_position p
    WHERE p.etf_instrument_id = (SELECT id FROM instrument WHERE symbol = 'TREZOR')
      AND p.holding_id = h.id
  );
