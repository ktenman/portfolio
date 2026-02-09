INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 19, 151.38, '2026-02-09', 'SWEDBANK', 4.03),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 2876.22, 1.00, '2026-02-09', 'SWEDBANK', 0);
