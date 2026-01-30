INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 1000, 1.0, '2026-01-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 1000, 1.0, '2026-01-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'SELL', 2.445589572, 16.14 / 2.445589572, '2026-01-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'SELL', 4.540637307, 38.05 / 4.540637307, '2026-01-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUDF:GER:EUR'), 'SELL', 1.279422704, 43.00 / 1.279422704, '2026-01-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 7.149956908, 248.89 / 7.149956908, '2026-01-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 2.841685022, 103.21 / 2.841685022, '2026-01-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'BUY', 3.717015104, 185.80 / 3.717015104, '2026-01-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VNRA:GER:EUR'), 'BUY', 1.570537428, 229.11 / 1.570537428, '2026-01-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 1.191395229, 185.81 / 1.191395229, '2026-01-30', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'CSX5:AEX:EUR'), 'BUY', 0.635571208, 144.37 / 0.635571208, '2026-01-30', 'LIGHTYEAR', 0);
