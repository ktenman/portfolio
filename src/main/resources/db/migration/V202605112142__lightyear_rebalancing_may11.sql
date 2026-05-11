INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 0.589700823, 113.14 / 0.589700823, '2026-05-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 3.884740455, 161.28 / 3.884740455, '2026-05-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'SELL', 1.774741506, 192.24 / 1.774741506, '2026-05-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'SELL', 83.142595978, 727.66 / 83.142595978, '2026-05-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 4.222929936, 51.71 / 4.222929936, '2026-05-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'CEMS:GER:EUR'), 'BUY', 5.009165902, 65.58 / 5.009165902, '2026-05-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 12.793415026, 103.36 / 12.793415026, '2026-05-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:AEX:EUR'), 'BUY', 6.010841036, 105.35 / 6.010841036, '2026-05-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 7.589916633, 286.79 / 7.589916633, '2026-05-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 10.922586691, 582.72 / 10.922586691, '2026-05-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 1.19, 1, '2026-05-11', 'LIGHTYEAR', 0);
