INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 16, 35.23, '2025-12-15', 'LHV');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR'), 'SELL', 1, 14.3010, '2025-12-15', 'LHV');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 1, 35.27, '2025-12-15', 'LHV');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 189, 35.4450, '2025-12-15', 'TRADING212');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 10, 35.4500, '2025-12-15', 'TRADING212');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 0.0036482, 35.4500, '2025-12-15', 'TRADING212');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 24, 152.1400, '2025-12-15', 'TRADING212');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 5, 152.1600, '2025-12-15', 'TRADING212');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 0.67297195, 152.1400, '2025-12-15', 'TRADING212');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'SELL', 29, 72.4950, '2025-12-15', 'TRADING212');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'SELL', 0.66964934, 72.4400, '2025-12-15', 'TRADING212');
