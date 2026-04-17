INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 76, 36.725, '2026-04-17', 'SWEDBANK', 3.91),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 2787.19, 1, '2026-04-17', 'SWEDBANK', 0);
