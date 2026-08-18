INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 0.52701218, 110.33 / 0.52701218, '2026-08-17', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VVSM:GER:EUR'), 'SELL', 1.197875442, 115.02 / 1.197875442, '2026-08-17', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'SEC0:GER:EUR'), 'SELL', 7.339313572, 131.73 / 7.339313572, '2026-08-17', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = '84X0:GER:EUR'), 'SELL', 18.436140964, 147.53 / 18.436140964, '2026-08-17', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'SELL', 13.346307079, 174.20 / 13.346307079, '2026-08-17', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'SELL', 3.257635215, 190.93 / 3.257635215, '2026-08-17', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'SELL', 22.673984632, 206.56 / 22.673984632, '2026-08-17', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 4.846230931, 217.62 / 4.846230931, '2026-08-17', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'SELL', 40.181790468, 395.43 / 40.181790468, '2026-08-17', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:AEX:EUR'), 'SELL', 18.936792452, 401.46 / 18.936792452, '2026-08-17', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'SELL', 10.08045977, 412.19 / 10.08045977, '2026-08-17', 'LIGHTYEAR', 0);
