INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'SELL', 53.690765926, 450.04 / 53.690765926, '2026-04-20', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 23.87032013, 879.86 / 23.87032013, '2026-04-20', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 5.398025508, 880.31 / 5.398025508, '2026-04-20', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'SELL', 45.653800748, 2387.24 / 45.653800748, '2026-04-20', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VNRA:GER:EUR'), 'SELL', 15.616952721, 2359.10 / 15.616952721, '2026-04-20', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'BUY', 29.54200542, 2616.24 / 29.54200542, '2026-04-20', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 27.865335814, 1647.12 / 27.865335814, '2026-04-20', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:AEX:EUR'), 'BUY', 62.359921506, 1104.26 / 62.359921506, '2026-04-20', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 81.405609789, 592.06 / 81.405609789, '2026-04-20', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 44.440502461, 523.60 / 44.440502461, '2026-04-20', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 11.347605708, 425.37 / 11.347605708, '2026-04-20', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUDF:GER:EUR'), 'BUY', 1.428209333, 47.90 / 1.428209333, '2026-04-20', 'LIGHTYEAR', 0);
