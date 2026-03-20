INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'SELL', 2840.38, 1, '2026-03-20', 'SWEDBANK', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUDF:GER:EUR'), 'BUY', 25, 33.61, '2026-03-20', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 12, 35.575, '2026-03-20', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR'), 'BUY', 23, 14.0665, '2026-03-20', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 46, 6.575, '2026-03-20', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 2, 61.64, '2026-03-20', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'BUY', 3, 48.76, '2026-03-20', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'ESIF:GER:EUR'), 'BUY', 11, 13.58, '2026-03-20', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'BUY', 2, 75.54, '2026-03-20', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VNRA:GER:EUR'), 'BUY', 1, 143.36, '2026-03-20', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VWCG:GER:EUR'), 'BUY', 1, 53.34, '2026-03-20', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 21, 8.38, '2026-03-20', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 3.03, 1, '2026-03-20', 'LHV', 0);
