-- Sell Vanguard S&P 500 (VUAA) on Trading212
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'VUAA:GER:EUR'), 'SELL', 21, 113.25, '2025-11-05', 'TRADING212');

-- Buy SPDR S&P 500 (SPYL) on Trading212 - First order
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR'), 'BUY', 104.0187475, 14.5006, '2025-11-05', 'TRADING212');

-- Buy SPDR S&P 500 (SPYL) on Trading212 - Second order
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR'), 'BUY', 59.99241405, 14.5005, '2025-11-05', 'TRADING212');
