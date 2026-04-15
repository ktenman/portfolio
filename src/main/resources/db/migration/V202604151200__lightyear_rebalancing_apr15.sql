INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'CEMS:GER:EUR'), 'SELL', 361.818343563, 5403.39 / 361.818343563, '2026-04-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VNRA:GER:EUR'), 'SELL', 18.703232323, 2777.43 / 18.703232323, '2026-04-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUDF:GER:EUR'), 'SELL', 21.930731416, 736.11 / 21.930731416, '2026-04-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 15.111874386, 538.97 / 15.111874386, '2026-04-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 3.305157774, 521.62 / 3.305157774, '2026-04-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'SELL', 4.783858267, 243.02 / 4.783858267, '2026-04-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'SELL', 21.635404454, 184.55 / 21.635404454, '2026-04-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:GER:EUR'), 'BUY', 384.547009528, 6699.58 / 384.547009528, '2026-04-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 47.324619898, 1774.20 / 47.324619898, '2026-04-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 221.445969125, 1549.24 / 221.445969125, '2026-04-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 6.249452954, 371.28 / 6.249452954, '2026-04-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBG:GER:EUR'), 'BUY', 0.930814354, 10.79 / 0.930814354, '2026-04-15', 'LIGHTYEAR', 0);
