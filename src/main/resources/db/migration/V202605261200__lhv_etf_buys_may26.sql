INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 24, 8.22, '2026-05-26', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'BUY', 6, 110.44, '2026-05-26', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 220, 12.518, '2026-05-26', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 33, 9.21, '2026-05-26', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'ESIF:GER:EUR'), 'BUY', 32, 15.47, '2026-05-26', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'BNKE:PAR:EUR'), 'BUY', 3, 343.85, '2026-05-26', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 25, 54.94, '2026-05-26', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = '84X0:GER:EUR'), 'BUY', 349, 8.01, '2026-05-26', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 9613.33, 1, '2026-05-26', 'LHV', 0);
