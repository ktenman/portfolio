INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'VVSM:GER:EUR'), 'BUY', 28.177745363, 2704.50 / 28.177745363, '2026-08-14', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:AEX:EUR'), 'BUY', 7.846135886, 168.03 / 7.846135886, '2026-08-14', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 3.294871794, 149.06 / 3.294871794, '2026-08-14', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 9.338127294, 122.07 / 9.338127294, '2026-08-14', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 2.833923988, 115.95 / 2.833923988, '2026-08-14', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 0.758130081, 44.76 / 0.758130081, '2026-08-14', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 0.091094268, 19.23 / 0.091094268, '2026-08-14', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'SEC0:GER:EUR'), 'SELL', 6.489392748, 115.63 / 6.489392748, '2026-08-14', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'SELL', 15.458765701, 141.53 / 15.458765701, '2026-08-14', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = '84X0:GER:EUR'), 'SELL', 28.811245974, 232.62 / 28.811245974, '2026-08-14', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'SELL', 24.89737932, 2833.82 / 24.89737932, '2026-08-14', 'LIGHTYEAR', 0);
