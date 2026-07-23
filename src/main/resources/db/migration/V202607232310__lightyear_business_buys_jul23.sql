INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 11.495459248, 100.00 / 11.495459248, '2026-07-23', 'LIGHTYEAR_BUSINESS', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'BUY', 0.514721021, 100.00 / 0.514721021, '2026-07-23', 'LIGHTYEAR_BUSINESS', 0),
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'BUY', 0.881678716, 100.00 / 0.881678716, '2026-07-23', 'LIGHTYEAR_BUSINESS', 0),
    ((SELECT id FROM instrument WHERE symbol = '84X0:GER:EUR'), 'BUY', 12.672918514, 100.00 / 12.672918514, '2026-07-23', 'LIGHTYEAR_BUSINESS', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:AEX:EUR'), 'BUY', 4.99845536, 100.00 / 4.99845536, '2026-07-23', 'LIGHTYEAR_BUSINESS', 0),
    ((SELECT id FROM instrument WHERE symbol = 'SEC0:GER:EUR'), 'BUY', 5.58347292, 100.00 / 5.58347292, '2026-07-23', 'LIGHTYEAR_BUSINESS', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 10.720411663, 100.00 / 10.720411663, '2026-07-23', 'LIGHTYEAR_BUSINESS', 0);
