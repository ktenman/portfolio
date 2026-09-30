INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 1.891076769, 15.17 / 1.891076769, '2026-09-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 1.081957661, 55.71 / 1.081957661, '2026-09-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VVSM:GER:EUR'), 'BUY', 0.947826086, 95.92 / 0.947826086, '2026-09-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'SEC0:GER:EUR'), 'BUY', 2.330886264, 43.82 / 2.330886264, '2026-09-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 0.200045341, 44.12 / 0.200045341, '2026-09-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VGLA:GER:EUR'), 'BUY', 8.93267802, 39.08 / 8.93267802, '2026-09-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 3.010672358, 39.49 / 3.010672358, '2026-09-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 0.563475221, 22.57 / 0.563475221, '2026-09-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 2.297447747, 109.37 / 2.297447747, '2026-09-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:AEX:EUR'), 'BUY', 6.805529075, 142.78 / 6.805529075, '2026-09-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = '84X0:GER:EUR'), 'BUY', 16.469774651, 138.13 / 16.469774651, '2026-09-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 14.759930915, 153.83 / 14.759930915, '2026-09-30', 'LIGHTYEAR', 0);
