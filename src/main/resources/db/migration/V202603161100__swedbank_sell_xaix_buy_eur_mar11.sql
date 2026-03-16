INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 18, 155.12, '2026-03-11', 'SWEDBANK', 3.91),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 2788.25, 1, '2026-03-11', 'SWEDBANK', 0);
