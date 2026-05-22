INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'SEC0:GER:EUR'), 'BUY', 137.123623154, 2340.43 / 137.123623154, '2026-05-22', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 5.938943726, 1167.24 / 5.938943726, '2026-05-22', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = '84X0:GER:EUR'), 'BUY', 112.253697093, 880.52 / 112.253697093, '2026-05-22', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 74.899307273, 605.49 / 74.899307273, '2026-05-22', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 5.9572697, 322.05 / 5.9572697, '2026-05-22', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:AEX:EUR'), 'BUY', 5.540418315, 98.01 / 5.540418315, '2026-05-22', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 0.881312319, 10.96 / 0.881312319, '2026-05-22', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 0.301135698, 12.86 / 0.301135698, '2026-05-22', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'SELL', 13.734534064, 526.17 / 13.734534064, '2026-05-22', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'SELL', 8.130895357, 882.69 / 8.130895357, '2026-05-22', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'CEMS:GER:EUR'), 'SELL', 130.603669489, 1751.40 / 130.603669489, '2026-05-22', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'SELL', 249.620842572, 2251.58 / 249.620842572, '2026-05-22', 'LIGHTYEAR', 0);
