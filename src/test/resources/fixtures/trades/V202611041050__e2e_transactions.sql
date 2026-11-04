INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'VUAA:GER:EUR'), 'BUY', 10, 100.00, '2025-01-06', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 5, 120.00, '2025-02-03', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'BTCEUR'), 'BUY', 0.01, 90000.00, '2025-03-03', 'TREZOR', 0);

UPDATE instrument SET current_price = 110.00 WHERE symbol = 'VUAA:GER:EUR';
UPDATE instrument SET current_price = 130.00 WHERE symbol = 'XAIX:GER:EUR';
UPDATE instrument SET current_price = 95000.00 WHERE symbol = 'BTCEUR';
