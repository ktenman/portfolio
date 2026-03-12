INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 2.79, 1, '2026-03-11', 'SWEDBANK', 0);
