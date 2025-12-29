INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'VNRA:GER:EUR'), 'SELL', 0.111578661, 147.3399706458, '2025-12-29', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'SELL', 0.280349583, 73.2299813455, '2025-12-29', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 0.30108429, 154.9398773034, '2025-12-29', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 0.067763524, 35.8600014764, '2025-12-29', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'CSX5:AEX:EUR'), 'BUY', 0.07768897, 218.9500455426, '2025-12-29', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'BUY', 0.456898267, 50.2299867689, '2025-12-29', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 1.177663524, 35.0099836424, '2025-12-29', 'LIGHTYEAR');
