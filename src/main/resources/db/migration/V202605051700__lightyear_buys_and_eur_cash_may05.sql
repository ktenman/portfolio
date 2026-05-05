INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 10.564697083, 84.75 / 10.564697083, '2026-05-05', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'BUY', 1.030642369, 101.24 / 1.030642369, '2026-05-05', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 9.205, 110.46 / 9.205, '2026-05-05', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'CEMS:GER:EUR'), 'BUY', 9.834885164, 126.75 / 9.834885164, '2026-05-05', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 21.182682682, 169.29 / 21.182682682, '2026-05-05', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 3.124824684, 178.24 / 3.124824684, '2026-05-05', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 5.700012825, 222.22 / 5.700012825, '2026-05-05', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:AEX:EUR'), 'BUY', 25.767958412, 436.20 / 25.767958412, '2026-05-05', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 15.298912313, 569.66 / 15.298912313, '2026-05-05', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 1.19, 1, '2026-05-05', 'LIGHTYEAR', 0);
