INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = '84X0:GER:EUR'), 'BUY', 0.847457627, 7.10 / 0.847457627, '2026-06-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 1.048149262, 10.45 / 1.048149262, '2026-06-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:AEX:EUR'), 'BUY', 2.352824796, 42.81 / 2.352824796, '2026-06-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 6.711577949, 84.98 / 6.711577949, '2026-06-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 0.404151459, 88.59 / 0.404151459, '2026-06-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 1.722071338, 95.11 / 1.722071338, '2026-06-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 2.685146785, 123.02 / 2.685146785, '2026-06-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 15.130281261, 124.27 / 15.130281261, '2026-06-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 3.515501362, 135.51 / 3.515501362, '2026-06-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'BUY', 1.15404083, 136.80 / 1.15404083, '2026-06-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'SEC0:GER:EUR'), 'BUY', 8.118536794, 151.36 / 8.118536794, '2026-06-02', 'LIGHTYEAR', 0);
