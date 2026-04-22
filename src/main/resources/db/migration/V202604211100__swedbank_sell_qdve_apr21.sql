INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 75, 37.30, '2026-04-21', 'SWEDBANK', 3.92),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 2793.58, 1, '2026-04-21', 'SWEDBANK', 0);
