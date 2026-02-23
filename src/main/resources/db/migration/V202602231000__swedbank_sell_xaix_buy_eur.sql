INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 19, 152.60, '2026-02-20', 'SWEDBANK', 4.06),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 2895.34, 1.00, '2026-02-20', 'SWEDBANK', 0);
