INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'BTCEUR'), 'BUY', 0.00858, 74781.14, '2025-12-30', 'BINANCE', 0.61),
    ((SELECT id FROM instrument WHERE symbol = 'BTCEUR'), 'BUY', 0.00477, 74781.14, '2025-12-30', 'BINANCE', 0.34),
    ((SELECT id FROM instrument WHERE symbol = 'BTCEUR'), 'SELL', 0.00001196, 74781.14, '2025-12-30', 'BINANCE', 0),
    ((SELECT id FROM instrument WHERE symbol = 'BTCEUR'), 'SELL', 0.000015, 74781.14, '2025-12-30', 'BINANCE', 0);
