INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 13719.16, 1, '2025-12-15', 'TRADING212', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 13719.16, 1, '2025-12-16', 'TRADING212', 0);
