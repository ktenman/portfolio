INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'SELL', 1.069395017, 66.11 / 1.069395017, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUDF:GER:EUR'), 'SELL', 0.561604584, 19.60 / 0.561604584, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'SELL', 8.643575418, 77.36 / 8.643575418, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VWCG:GER:EUR'), 'BUY', 1.153349616, 66.11 / 1.153349616, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 2.190816461, 82.78 / 2.190816461, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 1.036526301, 154.09 / 1.036526301, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 22.3984375, 143.35 / 22.3984375, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VWCG:GER:EUR'), 'BUY', 0.657307289, 37.69 / 0.657307289, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 6.739017296, 220.13 / 6.739017296, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'IS3S:GER:EUR'), 'BUY', 1.595377777, 89.74 / 1.595377777, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'BUY', 2.37367391, 115.23 / 2.37367391, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VNRA:GER:EUR'), 'BUY', 0.837774725, 121.98 / 0.837774725, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'CSX5:AEX:EUR'), 'BUY', 0.593569411, 136.61 / 0.593569411, '2026-03-02', 'LIGHTYEAR', 0);
