INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'SELL', 6.079697557, 59.50 / 6.079697557, '2026-06-29', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'SEC0:GER:EUR'), 'SELL', 2.031992001, 40.65 / 2.031992001, '2026-06-29', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = '84X0:GER:EUR'), 'SELL', 1.435309809, 11.84 / 1.435309809, '2026-06-29', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'SELL', 0.160540263, 18.78 / 0.160540263, '2026-06-29', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:AEX:EUR'), 'SELL', 3.256020942, 62.19 / 3.256020942, '2026-06-29', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'SELL', 2.406349206, 30.32 / 2.406349206, '2026-06-29', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'SELL', 10.982215862, 107.45 / 10.982215862, '2026-06-29', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'SELL', 2.683453237, 22.38 / 2.683453237, '2026-06-29', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'SELL', 0.266299918, 13.07 / 0.266299918, '2026-06-29', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'SELL', 1.478494623, 57.75 / 1.478494623, '2026-06-29', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 0.130126336, 26.78 / 0.130126336, '2026-06-29', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 1.165582495, 49.28 / 1.165582495, '2026-06-29', 'LIGHTYEAR', 0);
