INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 5, 7.174, '2026-04-17', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 1, 59.30, '2026-04-17', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'BNKE:PAR:EUR'), 'BUY', 1, 327.60, '2026-04-17', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 5, 8.351, '2026-04-17', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'BUY', 1, 82.32, '2026-04-17', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'BUY', 1, 51.80, '2026-04-17', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 4, 7.173, '2026-04-17', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 627.34, 1, '2026-04-17', 'LHV', 0);
