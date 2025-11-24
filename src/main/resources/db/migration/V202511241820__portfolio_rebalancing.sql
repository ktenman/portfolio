INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 454, 35.80, '2025-11-24', 'LIGHTYEAR');


INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 27, 150.70, '2025-11-24', 'LIGHTYEAR');


INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR'), 'SELL', 227, 14.26, '2025-11-24', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR'), 'SELL', 0.968735708, 14.26, '2025-11-24', 'LIGHTYEAR');


INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'BUY', 45, 70.65, '2025-11-24', 'LIGHTYEAR');


INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'VNRT:AEX:EUR'), 'BUY', 50, 142.16, '2025-11-24', 'LIGHTYEAR');


INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'CSX5:AEX:EUR'), 'BUY', 40, 211.05, '2025-11-24', 'LIGHTYEAR');


INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'WBIT:GER:EUR'), 'BUY', 260.322056862, 18.60, '2025-11-24', 'LIGHTYEAR');
