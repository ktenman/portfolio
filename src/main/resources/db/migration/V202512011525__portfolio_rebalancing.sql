-- Portfolio Rebalancing: 2025-12-01

-- ==================== TRADING212 ==================== --

-- SELL TRANSACTIONS
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR'), 'SELL', 97.65475388, 14.36, '2025-12-01', 'TRADING212');

-- BUY TRANSACTIONS
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'BUY', 5.54506982, 70.89, '2025-12-01', 'TRADING212');
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'BUY', 5.27637931, 70.98, '2025-12-01', 'TRADING212');
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 2.661568, 150.94, '2025-12-01', 'TRADING212');
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 6.53709157, 35.67, '2025-12-01', 'TRADING212');

-- ==================== LIGHTYEAR ==================== --

-- SELL TRANSACTIONS
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'VNRT:AEX:EUR'), 'SELL', 40.074120429, 142.86, '2025-12-01', 'LIGHTYEAR');
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'VWCE:GER:EUR'), 'SELL', 0.294359331, 143.60, '2025-12-01', 'LIGHTYEAR');
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'CSX5:AEX:EUR'), 'SELL', 0.265054638, 215.05, '2025-12-01', 'LIGHTYEAR');
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'SELL', 11.02950727, 70.83, '2025-12-01', 'LIGHTYEAR');
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 2.936050364, 150.90, '2025-12-01', 'LIGHTYEAR');
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 6.519842561, 35.64, '2025-12-01', 'LIGHTYEAR');

-- BUY TRANSACTIONS
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'WBIT:GER:EUR'), 'BUY', 10.577274967, 17.65, '2025-12-01', 'LIGHTYEAR');
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'VWCE:GER:EUR'), 'BUY', 0.061002785, 143.60, '2025-12-01', 'LIGHTYEAR');
INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'VNRA:GER:EUR'), 'BUY', 48.450751366, 146.40, '2025-12-01', 'LIGHTYEAR');
