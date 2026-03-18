INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 18, 158.02, '2026-03-18', 'SWEDBANK', 3.98),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 2840.38, 1, '2026-03-18', 'SWEDBANK', 0);
