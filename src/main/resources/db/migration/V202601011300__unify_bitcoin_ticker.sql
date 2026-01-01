UPDATE etf_holding
SET ticker = 'BTCEUR'
WHERE ticker = 'BTC' AND name = 'Bitcoin';

UPDATE etf_holding
SET name = 'Bitcoin'
WHERE name = 'Bitcoin (Trezor)';

UPDATE etf_holding
SET name = 'Binance Coin'
WHERE name = 'Binance Coin (Trezor)';
