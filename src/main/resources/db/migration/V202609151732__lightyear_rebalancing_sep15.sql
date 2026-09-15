INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'VVSM:GER:EUR'), 'BUY', 3.222347248, 286.37 / 3.222347248, '2026-09-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'SEC0:GER:EUR'), 'BUY', 24.448238416, 405.25 / 24.448238416, '2026-09-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 90.933572333, 868.15 / 90.933572333, '2026-09-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 2.452629063, 513.09 / 2.452629063, '2026-09-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'SELL', 15.664653328, 622.44 / 15.664653328, '2026-09-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 372.398024769, 4771.94 / 372.398024769, '2026-09-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = '84X0:GER:EUR'), 'BUY', 721.859267592, 5847.47 / 721.859267592, '2026-09-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'SELL', 446.836477987, 3552.35 / 446.836477987, '2026-09-14', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'SELL', 66.411559348, 3424.18 / 66.411559348, '2026-09-14', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 39.477993158, 1731.11 / 39.477993158, '2026-09-14', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:AEX:EUR'), 'SELL', 160.60138524, 3362.19 / 160.60138524, '2026-09-14', 'LIGHTYEAR', 0);
