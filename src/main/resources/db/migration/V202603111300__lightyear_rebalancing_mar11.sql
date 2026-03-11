INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 72.170644718, 2630.62 / 72.170644718, '2026-03-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 221.451187335, 2517.90 / 221.451187335, '2026-03-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 367.676145817, 2430.71 / 367.676145817, '2026-03-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'ESIF:GER:EUR'), 'BUY', 12.471982758, 173.61 / 12.471982758, '2026-03-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 11.016020139, 96.27 / 11.016020139, '2026-03-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUDF:GER:EUR'), 'BUY', 1.162209814, 39.55 / 1.162209814, '2026-03-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 0.325117561, 20.05 / 0.325117561, '2026-03-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VNRA:GER:EUR'), 'SELL', 0.110868675, 16.26 / 0.110868675, '2026-03-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 1.045637758, 160.84 / 1.045637758, '2026-03-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVF:GER:EUR'), 'SELL', 5.506566604, 190.78 / 5.506566604, '2026-03-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'CEMS:GER:EUR'), 'SELL', 198.294348508, 2442.59 / 198.294348508, '2026-03-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'IS3S:GER:EUR'), 'SELL', 45.250088279, 2477.89 / 45.250088279, '2026-03-11', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'SELL', 52.543613394, 2620.35 / 52.543613394, '2026-03-11', 'LIGHTYEAR', 0);
