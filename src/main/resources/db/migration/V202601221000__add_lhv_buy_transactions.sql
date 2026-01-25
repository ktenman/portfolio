INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 61, 8.7729508196721, '2026-01-22', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'CSX5:AEX:EUR'), 'BUY', 1, 223.15, '2026-01-22', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 32, 35.77, '2026-01-22', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 94, 6.4879787234042, '2026-01-22', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR'), 'BUY', 78, 14.3360256410256, '2026-01-22', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'BUY', 8, 49.47, '2026-01-22', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 3, 62.70, '2026-01-22', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 1, 62.70, '2026-01-22', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 6, 62.70, '2026-01-22', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 1260.00, 1.00, '2026-01-13', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 2804.07, 1.00, '2026-01-13', 'SWEDBANK', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 585.00, 1.00, '2026-01-16', 'LHV', 0);
