INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'SELL', 0.801925845, 89.11 / 0.801925845, '2026-05-25', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 0.503674081, 100.76 / 0.503674081, '2026-05-25', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'SEC0:GER:EUR'), 'SELL', 6.873105632, 121.54 / 6.873105632, '2026-05-25', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'SELL', 2.321206581, 126.97 / 2.321206581, '2026-05-25', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'SELL', 10.434782608, 131.04 / 10.434782608, '2026-05-25', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'SELL', 18.066893699, 148.55 / 18.066893699, '2026-05-25', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = '84X0:GER:EUR'), 'SELL', 19.074897016, 152.81 / 19.074897016, '2026-05-25', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 3.71574074, 160.52 / 3.71574074, '2026-05-25', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'SELL', 7.973118971, 309.96 / 7.973118971, '2026-05-25', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:AEX:EUR'), 'SELL', 19.727082875, 358.95 / 19.727082875, '2026-05-25', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'SELL', 43.256438, 399.78 / 43.256438, '2026-05-25', 'LIGHTYEAR', 0);
