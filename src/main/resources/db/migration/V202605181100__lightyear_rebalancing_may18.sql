INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = '84X0:GER:EUR'), 'BUY', 441.218177022, 3403.17 / 441.218177022, '2026-05-18', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 4.388182755, 38.32 / 4.388182755, '2026-05-18', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 12.841716838, 661.22 / 12.841716838, '2026-05-18', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:AEX:EUR'), 'BUY', 1.005187319, 17.44 / 1.005187319, '2026-05-18', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'BUY', 0.297641332, 31.80 / 0.297641332, '2026-05-18', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 3.185176414, 134.51 / 3.185176414, '2026-05-18', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'SELL', 65.779354838, 509.79 / 65.779354838, '2026-05-18', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'SELL', 3.685700341, 45.31 / 3.685700341, '2026-05-18', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 5.930971512, 1136.73 / 5.930971512, '2026-05-18', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'SELL', 31.841936774, 1193.60 / 31.841936774, '2026-05-18', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'CEMS:GER:EUR'), 'SELL', 88.053204729, 1132.01 / 88.053204729, '2026-05-18', 'LIGHTYEAR', 0);
