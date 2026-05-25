INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'SELL', 28, 58.88, '2026-05-22', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'SPYL:GER:EUR'), 'SELL', 30, 15.8810, '2026-05-22', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'SELL', 18, 99.78, '2026-05-22', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'SELL', 42, 38.35, '2026-05-22', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VNRA:GER:EUR'), 'SELL', 7, 161.44, '2026-05-22', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUDF:GER:EUR'), 'SELL', 67, 30.37, '2026-05-22', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VWCG:GER:EUR'), 'SELL', 16, 57.72, '2026-05-22', 'LHV', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 9620.20, 1, '2026-05-22', 'LHV', 0);
