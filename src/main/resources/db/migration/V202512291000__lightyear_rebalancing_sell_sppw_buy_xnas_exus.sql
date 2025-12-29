INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'SPPW:GER:EUR'), 'SELL', 175, 40.8030285714, '2025-12-29', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 6.88025899, 154.4999979717, '2025-12-29', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'CSX5:AEX:EUR'), 'SELL', 4.804010938, 219.4000000422, '2025-12-29', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'VNRA:GER:EUR'), 'SELL', 6.3512419, 148.1600000151, '2025-12-29', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 27.773144286, 35.9700000012, '2025-12-29', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'SELL', 15.671845189, 73.3800000020, '2025-12-29', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'BUY', 122.7143709, 50.3100000002, '2025-12-29', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 174.818880821, 35.3152358087, '2025-12-29', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'SPPW:GER:EUR'), 'SELL', 0.638397132, 40.6956715463, '2025-12-29', 'LIGHTYEAR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform)
VALUES ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 0.736290208, 35.2850000146, '2025-12-29', 'LIGHTYEAR');
