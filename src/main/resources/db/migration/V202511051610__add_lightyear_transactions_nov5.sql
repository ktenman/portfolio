-- Sell Vanguard S&P 500 (VUAA) on Lightyear
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'VUAA:GER:EUR'), 'SELL', 20, 113.285, '2025-11-05', 'LIGHTYEAR');

-- Buy SPDR S&P 500 (SPYL) on Lightyear
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR'), 'BUY', 156.842642394, 14.5019872484, '2025-11-05', 'LIGHTYEAR');
