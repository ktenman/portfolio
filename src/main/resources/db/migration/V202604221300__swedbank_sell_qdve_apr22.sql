INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 74, 38.00, '2026-04-22', 'SWEDBANK', 3.94),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 2808.06, 1, '2026-04-22', 'SWEDBANK', 0);
