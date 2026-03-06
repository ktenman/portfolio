INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 16478.46, 1, '2025-09-18', 'SWEDBANK', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 6329.85, 1, '2025-09-23', 'SWEDBANK', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 10148.61, 1, '2025-10-15', 'SWEDBANK', 0);
