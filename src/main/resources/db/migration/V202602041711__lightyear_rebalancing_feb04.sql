INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 0.196283695, 30.00 / 0.196283695, '2026-02-04', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VNRA:GER:EUR'), 'SELL', 0.20500205, 30.00 / 0.20500205, '2026-02-04', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 0.303459119, 46.32 / 0.303459119, '2026-02-04', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'BUY', 1.060156488, 52.17 / 1.060156488, '2026-02-04', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 3.874667454, 131.08 / 3.874667454, '2026-02-04', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 21.398998591, 136.76 / 21.398998591, '2026-02-04', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VWCG:GER:EUR'), 'BUY', 61.869234847, 3501.18 / 61.869234847, '2026-02-04', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'SELL', 1.617062065, 13.50 / 1.617062065, '2026-02-04', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'CSX5:AEX:EUR'), 'SELL', 5.426058489, 1243.11 / 5.426058489, '2026-02-04', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'IS3S:GER:EUR'), 'SELL', 24.034823191, 1338.98 / 24.034823191, '2026-02-04', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'SELL', 34.47872052, 1271.92 / 34.47872052, '2026-02-04', 'LIGHTYEAR', 0);
