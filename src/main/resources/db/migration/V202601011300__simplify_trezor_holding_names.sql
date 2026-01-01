-- Merge Bitcoin (Trezor) into existing Bitcoin holding
UPDATE etf_position
SET holding_id = (SELECT id FROM etf_holding WHERE name = 'Bitcoin' AND ticker = 'BTC' LIMIT 1)
WHERE holding_id = (SELECT id FROM etf_holding WHERE name = 'Bitcoin (Trezor)' LIMIT 1)
  AND EXISTS (SELECT 1 FROM etf_holding WHERE name = 'Bitcoin' AND ticker = 'BTC');

DELETE FROM etf_holding WHERE name = 'Bitcoin (Trezor)'
  AND EXISTS (SELECT 1 FROM etf_holding WHERE name = 'Bitcoin' AND ticker = 'BTC');

-- Rename if no existing Bitcoin holding to merge into
UPDATE etf_holding SET name = 'Bitcoin' WHERE name = 'Bitcoin (Trezor)';

-- Merge Binance Coin (Trezor) into existing Binance Coin holding
UPDATE etf_position
SET holding_id = (SELECT id FROM etf_holding WHERE name = 'Binance Coin' LIMIT 1)
WHERE holding_id = (SELECT id FROM etf_holding WHERE name = 'Binance Coin (Trezor)' LIMIT 1)
  AND EXISTS (SELECT 1 FROM etf_holding WHERE name = 'Binance Coin');

DELETE FROM etf_holding WHERE name = 'Binance Coin (Trezor)'
  AND EXISTS (SELECT 1 FROM etf_holding WHERE name = 'Binance Coin');

-- Rename if no existing Binance Coin holding to merge into
UPDATE etf_holding SET name = 'Binance Coin' WHERE name = 'Binance Coin (Trezor)';
