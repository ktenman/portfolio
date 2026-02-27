INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 8.509911768, 74.27 / 8.509911768, '2026-02-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 2.533176593, 96.97 / 2.533176593, '2026-02-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUDF:GER:EUR'), 'BUY', 3.087701466, 106.33 / 3.087701466, '2026-02-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'CSX5:AEX:EUR'), 'BUY', 0.574681393, 135.28 / 0.574681393, '2026-02-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VWCG:GER:EUR'), 'BUY', 2.669837426, 154.37 / 2.669837426, '2026-02-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'IS3S:GER:EUR'), 'BUY', 3.076365564, 174.03 / 3.076365564, '2026-02-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 29.387501914, 191.87 / 29.387501914, '2026-02-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 3.788939624, 224.04 / 3.788939624, '2026-02-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 8.566199261, 290.18 / 8.566199261, '2026-02-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VNRA:GER:EUR'), 'BUY', 2.010665937, 294.08 / 2.010665937, '2026-02-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 2.107716618, 320.12 / 2.107716618, '2026-02-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'BUY', 6.668984558, 326.08 / 6.668984558, '2026-02-27', 'LIGHTYEAR', 0);
