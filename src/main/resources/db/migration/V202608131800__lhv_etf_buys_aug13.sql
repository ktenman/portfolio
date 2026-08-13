INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'BNKE:PAR:EUR'), 'BUY', 1, 408.65, '2026-08-13', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 11, 9.892, '2026-08-13', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = '84X0:GER:EUR'), 'BUY', 5, 8.019, '2026-08-13', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 8, 9.20, '2026-08-13', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 1.16, 1, '2026-08-13', 'LHV', 0);
