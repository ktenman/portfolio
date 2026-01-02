INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'BTCEUR'), 'BUY', 0.05191, 75970.84, '2026-01-02', 'BINANCE', 3.75),
    ((SELECT id FROM instrument WHERE symbol = 'BTCEUR'), 'SELL', 0.00004931, 75970.84, '2026-01-02', 'BINANCE', 0),
    ((SELECT id FROM instrument WHERE symbol = 'BTCEUR'), 'SELL', 0.000015, 75970.84, '2026-01-02', 'BINANCE', 0);
