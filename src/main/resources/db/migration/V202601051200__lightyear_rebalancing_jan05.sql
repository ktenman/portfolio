INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'IS3S:GER:EUR'), 'BUY', 86.912600846, 52.180120671290, '2026-01-05', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'SELL', 8.050209205, 50.190000000130, '2026-01-05', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 2.685857161, 155.980000010134, '2026-01-05', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'SELL', 7.095654478, 75.480000000078, '2026-01-05', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VNRA:GER:EUR'), 'SELL', 2.976335361, 147.900000036319, '2026-01-05', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 10.073187895, 35.525000002990, '2026-01-05', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'SELL', 13.480078288, 35.765370919928, '2026-01-05', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'SELL', 80.986348542, 8.131000000061, '2026-01-05', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'SELL', 13.49383378, 55.950000000666, '2026-01-05', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'CSX5:AEX:EUR'), 'SELL', 2.143364403, 225.300000001912, '2026-01-05', 'LIGHTYEAR', 0);
