INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 2793.58, 1, '2026-04-23', 'SWEDBANK', 0),
    ((SELECT id FROM instrument WHERE symbol = 'BNKE:PAR:EUR'), 'BUY', 5, 320.35, '2026-04-23', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'BUY', 6, 92.63, '2026-04-23', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 7, 57.78, '2026-04-23', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 17, 8.014, '2026-04-23', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 9, 7.416, '2026-04-23', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'ESIF:GER:EUR'), 'BUY', 1, 14.784, '2026-04-23', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 1, 7.423, '2026-04-23', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 6.41, 1, '2026-04-23', 'LHV', 0);
