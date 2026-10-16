UPDATE portfolio_transaction
SET platform = 'REVOLUT_X'
WHERE platform = 'BINANCE'
  AND transaction_date = '2026-10-15'
  AND instrument_id = (SELECT id FROM instrument WHERE symbol = 'EUR');

UPDATE portfolio_transaction
SET platform = 'TREZOR'
WHERE platform = 'BINANCE';

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 5000, 1, '2026-10-16', 'REVOLUT_X', 0),
    ((SELECT id FROM instrument WHERE symbol = 'BTCEUR'), 'BUY', 0.06844626, 5000.00 / 0.06844626, '2026-10-16', 'REVOLUT_X', 0);

UPDATE diversification_config
SET config_data = replace(config_data, '"BINANCE"', '"REVOLUT_X","TREZOR"');
