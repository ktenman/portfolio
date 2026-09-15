INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'SELL', 22, 105.80, '2026-09-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'ESIF:GER:EUR'), 'SELL', 131, 16.956, '2026-09-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'SELL', 125, 8.004, '2026-09-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'SELL', 52, 51.29, '2026-09-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'BNKE:PAR:EUR'), 'BUY', 1, 399.50, '2026-09-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'BNKE:PAR:EUR'), 'BUY', 1, 399.55, '2026-09-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'BNKE:PAR:EUR'), 'BUY', 3, 400.55, '2026-09-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR'), 'BUY', 34, 16.3375, '2026-09-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 48, 12.764, '2026-09-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 48, 12.760, '2026-09-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 48, 12.760, '2026-09-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 48, 12.760, '2026-09-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 48, 12.760, '2026-09-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 48, 12.760, '2026-09-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 3, 12.762, '2026-09-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 201, 12.766, '2026-09-15', 'LHV', 0);
