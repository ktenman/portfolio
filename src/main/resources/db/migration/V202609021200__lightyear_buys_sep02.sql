INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'SEC0:GER:EUR'), 'BUY', 4.312884189, 70.16 / 4.312884189, '2026-09-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VVSM:GER:EUR'), 'BUY', 1.465206924, 127.81 / 1.465206924, '2026-09-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 0.66030027, 134.14 / 0.66030027, '2026-09-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = '84X0:GER:EUR'), 'BUY', 22.267132692, 178.38 / 22.267132692, '2026-09-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 14.205104117, 181.46 / 14.205104117, '2026-09-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 3.565142857, 187.17 / 3.565142857, '2026-09-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 24.879147493, 205.45 / 24.879147493, '2026-09-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 5.709854641, 249.44 / 5.709854641, '2026-09-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:AEX:EUR'), 'BUY', 17.632730732, 370.64 / 17.632730732, '2026-09-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 42.211486849, 393.20 / 42.211486849, '2026-09-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 10.071374906, 402.15 / 10.071374906, '2026-09-02', 'LIGHTYEAR', 0);
