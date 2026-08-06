INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 0.382030769, 15.52 / 0.382030769, '2026-08-06', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 0.349656862, 71.33 / 0.349656862, '2026-08-06', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 6.604633204, 85.53 / 6.604633204, '2026-08-06', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 2.7696793, 123.50 / 2.7696793, '2026-08-06', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 17.866486148, 171.55 / 17.866486148, '2026-08-06', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:AEX:EUR'), 'BUY', 13.805587892, 296.48 / 13.805587892, '2026-08-06', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'BUY', 3.183363309, 353.99 / 3.183363309, '2026-08-06', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'SEC0:GER:EUR'), 'BUY', 22.073945696, 382.10 / 22.073945696, '2026-08-06', 'LIGHTYEAR', 0);
