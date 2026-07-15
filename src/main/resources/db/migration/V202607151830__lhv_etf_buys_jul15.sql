INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'BUY', 1, 116.34, '2026-07-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 1, 39.705, '2026-07-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'ESIF:GER:EUR'), 'BUY', 2, 16.662, '2026-07-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 1, 12.810, '2026-07-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 6, 9.688, '2026-07-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = '84X0:GER:EUR'), 'BUY', 4, 8.038, '2026-07-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR'), 'BUY', 1, 16.3600, '2026-07-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 8, 8.43, '2026-07-15', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 1.40, 1, '2026-07-15', 'LHV', 0);
